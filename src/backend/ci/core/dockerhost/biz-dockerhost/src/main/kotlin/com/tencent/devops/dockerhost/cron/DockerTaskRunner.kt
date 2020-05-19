/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.dockerhost.cron

import com.tencent.devops.common.web.mq.alert.AlertLevel
import com.tencent.devops.dispatch.pojo.enums.PipelineTaskStatus
import com.tencent.devops.dockerhost.dispatch.AlertApi
import com.tencent.devops.dockerhost.exception.ContainerException
import com.tencent.devops.dockerhost.exception.NoSuchImageException
import com.tencent.devops.dockerhost.services.DockerHostBuildService
import com.tencent.devops.dockerhost.utils.CommonUtils
import com.tencent.devops.dockerhost.utils.SigarUtil
import com.tencent.devops.process.engine.common.VMUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

// @Component
class DockerTaskRunner @Autowired constructor(private val dockerHostBuildService: DockerHostBuildService) {
    private val logger = LoggerFactory.getLogger(DockerTaskRunner::class.java)
    private val maxRunningContainerNum = 200
    private val alertApi: AlertApi = AlertApi()

    //    @Scheduled(initialDelay = 60 * 1000, fixedDelay = 5 * 1000)
    fun startBuild() {
        logger.info("Start to start build")
        try {
            // 优先判断机器负载
            if (!SigarUtil.loadEnable()) {
                logger.warn("Docker构建机负载过高, 正在尝试其他构建机, cpuLoad: ${SigarUtil.getAverageCpuLoad()}, memLoad: ${SigarUtil.getAverageMemLoad()}")
                alertApi.alert(AlertLevel.HIGH.name, "Docker构建机负载过高", "Docker构建机负载过高, " +
                    "母机IP:${CommonUtils.getInnerIP()}， cpuLoad: ${SigarUtil.getAverageCpuLoad()}, memLoad: ${SigarUtil.getAverageMemLoad()}, memQueue: ${SigarUtil.getMemQueue()}")
                return
            }

            val containerNum = dockerHostBuildService.getContainerNum()
            if (containerNum >= maxRunningContainerNum) {
                logger.warn("Too many containers in this host, break to start build.")
                alertApi.alert(AlertLevel.HIGH.name, "Docker构建机运行的容器太多", "Docker构建机运行的容器太多, " +
                    "母机IP:${CommonUtils.getInnerIP()}， 容器数量: $containerNum")
                return
            }

            val dockerStartBuildInfo = try {
                dockerHostBuildService.startBuild()
            } catch (e: Exception) {
                logger.warn("Fail to start build", e)
                return
            }
            if (dockerStartBuildInfo != null) {
                if (dockerStartBuildInfo.status == PipelineTaskStatus.RUNNING.status) {
                    logger.warn("Create container, dockerStartBuildInfo: $dockerStartBuildInfo")

                    try {
                        val containerId = dockerHostBuildService.createContainer(dockerStartBuildInfo)
                        // 上报containerId给dispatch
                        dockerHostBuildService.reportContainerId(
                            buildId = dockerStartBuildInfo.buildId,
                            vmSeqId = dockerStartBuildInfo.vmSeqId,
                            containerId = containerId
                        )

                        if (dockerHostBuildService.isContainerRunning(containerId)) {
                            dockerHostBuildService.log(
                                buildId = dockerStartBuildInfo.buildId,
                                message = "构建环境启动成功，等待Agent启动...",
                                tag = VMUtils.genStartVMTaskId(dockerStartBuildInfo.vmSeqId.toString()),
                                containerHashId = dockerStartBuildInfo.containerHashId
                            )
                        } else {
                            logger.error("Create container container failed, no such image. pipelineId: ${dockerStartBuildInfo.pipelineId}, vmSeqId: ${dockerStartBuildInfo.vmSeqId}")
                            dockerHostBuildService.rollbackBuild(
                                buildId = dockerStartBuildInfo.buildId,
                                vmSeqId = dockerStartBuildInfo.vmSeqId,
                                shutdown = true,
                                containerId = dockerStartBuildInfo.vmSeqId.toString(),
                                containerHashId = dockerStartBuildInfo.containerHashId
                            )
                        }
                    } catch (e: NoSuchImageException) {
                        logger.error("Create container container failed, no such image. pipelineId: ${dockerStartBuildInfo.pipelineId}, vmSeqId: ${dockerStartBuildInfo.vmSeqId}, err: ${e.message}")
                        dockerHostBuildService.rollbackBuild(
                            buildId = dockerStartBuildInfo.buildId,
                            vmSeqId = dockerStartBuildInfo.vmSeqId,
                            shutdown = true,
                            containerId = dockerStartBuildInfo.vmSeqId.toString(),
                            containerHashId = dockerStartBuildInfo.containerHashId
                        )
                    } catch (e: ContainerException) {
                        logger.error("Create container failed, rollback build. buildId: ${dockerStartBuildInfo.buildId}, vmSeqId: ${dockerStartBuildInfo.vmSeqId}")
                        dockerHostBuildService.rollbackBuild(
                            buildId = dockerStartBuildInfo.buildId,
                            vmSeqId = dockerStartBuildInfo.vmSeqId,
                            shutdown = false,
                            containerId = dockerStartBuildInfo.vmSeqId.toString(),
                            containerHashId = dockerStartBuildInfo.containerHashId
                        )
                    }
                }
            } else {
                logger.info("Get empty docker start build info")
            }
        } catch (t: Throwable) {
            logger.error("StartBuild encounter unknown exception", t)
        }
    }

    //    @Scheduled(initialDelay = 120 * 1000, fixedDelay = 20 * 1000)
    fun endBuild() {
        try {
            val dockerEndBuildInfo = try {
                dockerHostBuildService.endBuild()
            } catch (e: Exception) {
                logger.warn("Fail to end build", e)
                return
            }
            if (dockerEndBuildInfo != null) {
                logger.warn("dockerEndBuildInfo: $dockerEndBuildInfo")
                if (dockerEndBuildInfo.status == PipelineTaskStatus.DONE.status || dockerEndBuildInfo.status == PipelineTaskStatus.FAILURE.status) {
                    logger.warn("Stop the container, containerId: ${dockerEndBuildInfo.containerId}")
                    dockerHostBuildService.stopContainer(dockerEndBuildInfo)
                }
            }
        } catch (t: Throwable) {
            logger.error("EndBuild encounter unknown exception", t)
        }
    }
}
