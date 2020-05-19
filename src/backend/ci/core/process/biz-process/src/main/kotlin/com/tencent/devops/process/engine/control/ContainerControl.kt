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

package com.tencent.devops.process.engine.control

import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.event.enums.ActionType
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.ContainerMutexStatus
import com.tencent.devops.common.pipeline.enums.EnvControlTaskType
import com.tencent.devops.common.pipeline.enums.JobRunCondition
import com.tencent.devops.common.pipeline.pojo.element.RunCondition
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.log.utils.LogUtils
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.engine.control.ControlUtils.continueWhenFailure
import com.tencent.devops.process.engine.control.lock.ContainerIdLock
import com.tencent.devops.process.engine.pojo.PipelineBuildContainer
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.process.engine.pojo.event.PipelineBuildAtomTaskEvent
import com.tencent.devops.process.engine.pojo.event.PipelineBuildStageEvent
import com.tencent.devops.process.engine.service.PipelineBuildDetailService
import com.tencent.devops.process.engine.service.PipelineRuntimeService
import com.tencent.devops.common.api.pojo.ErrorCode
import com.tencent.devops.common.api.pojo.ErrorType
import com.tencent.devops.process.engine.common.BS_CONTAINER_END_SOURCE_PREIX
import com.tencent.devops.process.pojo.mq.PipelineBuildContainerEvent
import com.tencent.devops.process.service.BuildVariableService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 *  Job（运行容器）控制器
 * @version 1.0
 */
@Service
class ContainerControl @Autowired constructor(
    private val rabbitTemplate: RabbitTemplate,
    private val redisOperation: RedisOperation,
    private val pipelineEventDispatcher: PipelineEventDispatcher,
    private val pipelineRuntimeService: PipelineRuntimeService,
    private val pipelineBuildDetailService: PipelineBuildDetailService,
    private val buildVariableService: BuildVariableService,
    private val mutexControl: MutexControl
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun handle(event: PipelineBuildContainerEvent) {
        with(event) {
            val containerIdLock = ContainerIdLock(redisOperation, buildId, containerId)
            try {
                containerIdLock.lock()
                execute()
            } finally {
                containerIdLock.unlock()
            }
        }
    }

    private fun PipelineBuildContainerEvent.execute() {
        val container = pipelineRuntimeService.getContainer(buildId, stageId, containerId) ?: run {
            logger.warn("[$buildId]|bad container|stage=$stageId|container=$containerId")
            return
        }

        // 当build的状态是结束的时候，直接返回
        if (BuildStatus.isFinish(container.status)) {
            return
        }

        // Container互斥组的判断
        // 并初始化互斥组的值
        val variables = buildVariableService.getAllVariable(buildId)
        val mutexGroup = mutexControl.initMutexGroup(
            mutexGroup = container.controlOption?.mutexGroup,
            variables = variables
        )
        val containerTaskList = pipelineRuntimeService.listContainerBuildTasks(buildId, containerId)

        // 仅在初次进入Container时进行跳过判断
        if (BuildStatus.isReadyToRun(container.status)) {
            if (checkIfAllSkip(
                    buildId = buildId,
                    stageId = stageId,
                    container = container,
                    containerTaskList = containerTaskList,
                    variables = variables
                )
            ) {
                pipelineRuntimeService.updateContainerStatus(
                    buildId = buildId,
                    stageId = stageId,
                    containerId = containerId,
                    buildStatus = BuildStatus.SKIP,
                    startTime = LocalDateTime.now(),
                    endTime = LocalDateTime.now()
                )

                containerTaskList.forEach {
                    pipelineRuntimeService.updateTaskStatus(
                        buildId = buildId,
                        taskId = it.taskId,
                        userId = it.starter,
                        buildStatus = BuildStatus.SKIP
                    )
                }

                logger.info("[$buildId]|CONTAINER_SKIP|stage=$stageId|container=$containerId|action=$actionType")
                pipelineBuildDetailService.normalContainerSkip(buildId, container.containerId)
                // 返回stage的时候，需要解锁
                mutexControl.releaseContainerMutex(
                    projectId = projectId,
                    buildId = buildId,
                    stageId = stageId,
                    containerId = containerId,
                    mutexGroup = mutexGroup
                )
                return sendBackStage("container_skip")
            }
        }

        // 终止或者结束事件，跳过是假货和不启动job配置，都不做互斥判断
        if (!ActionType.isEnd(actionType) && container.controlOption?.jobControlOption?.enable != false) {
            val mutexResult =
                mutexControl.checkContainerMutex(
                    projectId = projectId,
                    buildId = buildId,
                    stageId = stageId,
                    containerId = containerId,
                    mutexGroup = mutexGroup,
                    container = container
                )
            logger.info("[$buildId]|MUTEX_START|stage=$stageId|container=$containerId|action=$actionType|projectId=$projectId")
            when (mutexResult) {
                ContainerMutexStatus.CANCELED -> {
                    logger.info("[$buildId]|MUTEX_CANCEL|stage=$stageId|container=$containerId|action=$actionType|projectId=$projectId")
                    // job互斥失败的时候，设置container状态为失败
                    pipelineRuntimeService.updateContainerStatus(
                        buildId = buildId,
                        stageId = stageId,
                        containerId = containerId,
                        buildStatus = BuildStatus.FAILED,
                        startTime = LocalDateTime.now(),
                        endTime = LocalDateTime.now()
                    )
                    // job互斥失败的时候，设置详情页面为失败。
                    pipelineBuildDetailService.updateContainerStatus(buildId, containerId, BuildStatus.FAILED)
                    return sendBackStage("container_mutex_cancel")
                }
                ContainerMutexStatus.WAITING -> {
                    logger.info("[$buildId]|MUTEX_DELAY|stage=$stageId|container=$containerId|action=$actionType|projectId=$projectId")
                    return sendSelfDelay()
                }
                else -> {
                    logger.info("[$buildId]|MUTEX_RUNNING|stage=$stageId|container=$containerId|action=$actionType|projectId=$projectId")
                    // 正常运行
                }
            }
        }

        //   待执行任务， job状态， 是否启动构建机任务而失败
        val (waitToDoTask, containerFinalStatus, startVMFail) =
            when {
                // 要求启动执行的请求
                ActionType.isStart(actionType) || ActionType.REFRESH == actionType -> checkStartAction(containerTaskList)
                    ?: return
                // 要求强制终止
                ActionType.isTerminate(actionType) -> {
                    checkTerminateAction(containerTaskList, reason)
                }
                // 要求停止执行的请求
                ActionType.isEnd(actionType) -> {
                    checkEndAction(containerTaskList)
                }
                else -> { // 未规定的类型，打回上一级处理
                    logger.warn("[$buildId]|CONTAINER_UNKNOWN_ACTION|stage=$stageId|container=$containerId|actionType=$actionType")
                    // 返回stage的时候，需要解锁
                    mutexControl.releaseContainerMutex(
                        projectId = projectId,
                        buildId = buildId,
                        stageId = stageId,
                        containerId = containerId,
                        mutexGroup = mutexGroup
                    )
                    return sendBackStage("CONTAINER_UNKNOWN_ACTION")
                }
            }

        // 容器构建失败
        if (waitToDoTask == null && BuildStatus.isFailure(containerFinalStatus)) {
            if (!startVMFail) { // 非构建机启动失败的 做下收尾动作
                containerTaskList.forEach {
                    if (taskNeedRunWhenOtherTaskFail(it)) {
                        logger.info("[$buildId]|CONTAINER_$actionType|stage=$stageId|container=$containerId|taskId=${it.taskId}|Continue when failed")
                        return sendTask(it, ActionType.START)
                    }
                }
            }
            pipelineBuildDetailService.updateStartVMStatus(buildId, containerId, containerFinalStatus)
            val finallyTasks = containerTaskList.filter { task ->
                if (task.taskId == VMUtils.genEndPointTaskId(task.taskSeq) || // end-xxx 结束拦截点
                    task.taskId == VMUtils.genStopVMTaskId(task.taskSeq) // 停止构建机
                ) {
                    true
                } else {
                    if (BuildStatus.isReadyToRun(task.status)) {
                        // 将排队中的任务全部置为未执行状态
                        pipelineRuntimeService.updateTaskStatus(buildId, task.taskId, userId, BuildStatus.UNEXEC)
                        // logCoverUnExecTask(task, "Job失败！未执行的插件[${task.taskName}]")
                    }
                    false
                }
            }

            // 确认最后一步是清理构建环境的并且还未执行完成的，则选择为要下发执行
            if (finallyTasks.size == 2) {
                when {
                    // 如果是非构建机启动失败或者关闭操作的，先拿结束点Hold住让构建机来认领结束，否则都是直接关闭构建机
                    !startVMFail && !ActionType.isEnd(actionType) && BuildStatus.isReadyToRun(finallyTasks[0].status) ->
                        return sendTask(finallyTasks[0], ActionType.START) // 先拿结束点
                    BuildStatus.isReadyToRun(finallyTasks[1].status) -> {
                        // 先将Hold点移出待执行状态，置为未执行。
                        pipelineRuntimeService.updateTaskStatus(
                            buildId = buildId,
                            taskId = finallyTasks[0].taskId,
                            userId = userId,
                            buildStatus = BuildStatus.UNEXEC
                        )
                        return sendTask(finallyTasks[1], ActionType.START) // 再拿停止构建机
                    }
                }
            }
        }

        // Job在运行中需要更新状态， 或者没有任务的情况下要结束了，才更新Job的状态。
        if (!BuildStatus.isFinish(containerFinalStatus) || waitToDoTask == null) {
            val startTime = if (BuildStatus.isReadyToRun(container.status)) {
                LocalDateTime.now()
            } else null
            val endTime = if (BuildStatus.isFinish(containerFinalStatus)) {
                LocalDateTime.now()
            } else null

            pipelineRuntimeService.updateContainerStatus(
                buildId = buildId,
                stageId = stageId,
                containerId = containerId,
                startTime = startTime,
                endTime = endTime,
                buildStatus = containerFinalStatus
            )
        }

        logger.info("[$buildId]|startVMFail=$startVMFail|task=${waitToDoTask?.taskName}|status=$containerFinalStatus")
        // 当前能处理的任务为空
        if (waitToDoTask == null) {
            // 旧的实现方式，整个Model的修改，暂时保留
            pipelineBuildDetailService.updateContainerStatus(buildId, containerId, containerFinalStatus)
            logger.info("[$buildId]|CONTAINER_END|stage=$stageId|container=$containerId|action=$actionType|status=$containerFinalStatus")
            // 返回stage的时候，需要解锁
            mutexControl.releaseContainerMutex(
                projectId = projectId,
                buildId = buildId,
                stageId = stageId,
                containerId = containerId,
                mutexGroup = mutexGroup
            )
            sendBackStage("$BS_CONTAINER_END_SOURCE_PREIX$containerFinalStatus")
        } else {
            sendTask(waitToDoTask, actionType)
        }
    }

    private fun taskNeedRunWhenOtherTaskFail(task: PipelineBuildTask): Boolean {
        // wait to run
        if (!BuildStatus.isReadyToRun(task.status)) {
            return false
        }

        val runCondition = task.additionalOptions?.runCondition
        return if (runCondition == null)
            false
        else runCondition == RunCondition.PRE_TASK_FAILED_BUT_CANCEL ||
            runCondition == RunCondition.PRE_TASK_FAILED_ONLY
    }

    private fun checkTerminateAction(containerTaskList: Collection<PipelineBuildTask>, message: String?): Triple<Nothing?, BuildStatus, Boolean> {
        var startVMFail = false
        var containerFinalStatus: BuildStatus = BuildStatus.FAILED
        containerTaskList.forEach { task ->
            when {
                BuildStatus.isRunning(task.status) -> {
                    containerFinalStatus = BuildStatus.FAILED
                    pipelineRuntimeService.updateTaskStatus(
                        buildId = task.buildId,
                        taskId = task.taskId,
                        userId = task.starter,
                        buildStatus = containerFinalStatus
                    )
                    LogUtils.addRedLine(
                        rabbitTemplate = rabbitTemplate,
                        buildId = task.buildId, message = "终止执行插件[${task.taskName}]: $message",
                        tag = task.taskId, jobId = task.containerHashId, executeCount = task.executeCount ?: 1
                    )
                    pipelineBuildDetailService.taskEnd(
                        buildId = task.buildId,
                        taskId = task.taskId,
                        buildStatus = task.status,
                        canRetry = true,
                        errorType = ErrorType.SYSTEM,
                        errorCode = ErrorCode.SYSTEM_WORKER_INITIALIZATION_ERROR,
                        errorMsg = message ?: "插件执行意外终止"
                    )
                    startVMFail = startVMFail || task.taskSeq == 0
                }
                BuildStatus.isFailure(task.status) -> {
                    containerFinalStatus = task.status
                    startVMFail = startVMFail || task.taskSeq == 0
                }
                else -> containerFinalStatus = BuildStatus.FAILED
            }
        }
        return Triple(null, containerFinalStatus, startVMFail)
    }

    private fun checkEndAction(containerTaskList: Collection<PipelineBuildTask>): Triple<PipelineBuildTask?, BuildStatus, Boolean> {
        var waitToDoTask: PipelineBuildTask? = null
        var containerFinalStatus: BuildStatus = BuildStatus.SUCCEED
        var startVMFail = false
        containerTaskList.forEach { task ->
            if (waitToDoTask == null && BuildStatus.isRunning(task.status)) {
                // 拿到按序号排列的第一个正在执行的插件
                waitToDoTask = task
            } else if (BuildStatus.isFailure(task.status)) {
                containerFinalStatus = task.status
                if (waitToDoTask != null) {
                    waitToDoTask = null
                }
                startVMFail = task.taskSeq == 0
                return Triple(waitToDoTask, containerFinalStatus, startVMFail)
            }
        }
        return Triple(waitToDoTask, containerFinalStatus, startVMFail)
    }

    private fun PipelineBuildContainerEvent.checkStartAction(
        containerTaskList: Collection<PipelineBuildTask>
    ): Triple<PipelineBuildTask?, BuildStatus, Boolean>? {

        var waitToDoTask: PipelineBuildTask? = null
        var containerFinalStatus: BuildStatus = BuildStatus.SUCCEED
        var hasFailedTaskInSuccessContainer = false
        var startVMFail = false

        containerTaskList.forEach nextOne@{ task ->
            if (!ControlUtils.isEnable(task.additionalOptions)) {
                logger.info("[$buildId]|container=$containerId|task(${task.taskSeq})=${task.taskId}|${task.taskName}|is not enable, will skip")

                LogUtils.addYellowLine(
                    rabbitTemplate = rabbitTemplate,
                    buildId = task.buildId,
                    message = "插件[${task.taskName}]被禁用",
                    tag = task.taskId,
                    jobId = task.containerHashId,
                    executeCount = task.executeCount ?: 1
                )

                return@nextOne
            }

            logger.info(
                "[$buildId]|container=$containerId|task(${task.taskSeq})=${task.taskId}|${task.taskName}|${task.status}"
            )

            // 防止重复的发送启动同一个容器构建的消息：容器中的任务要求串行执行，所以再次启动会直接当作成功结束返回。
            if (BuildStatus.isRunning(task.status)) {
                containerFinalStatus = BuildStatus.RUNNING
                logger.warn("[$buildId]|CONTAINER_CURRENT|container=$containerId|type=$actionType|running_task=$task")
                return null
            } else if (waitToDoTask == null && BuildStatus.isReadyToRun(task.status)) {
                // 拿到按序号排列的第一个待执行的插件
                waitToDoTask = task
                val variables = buildVariableService.getAllVariable(buildId)
                if (ControlUtils.checkAdditionalSkip(
                        task.buildId,
                        task.additionalOptions,
                        containerFinalStatus,
                        variables,
                        hasFailedTaskInSuccessContainer
                    )
                ) {
                    logger.warn(
                        "[$buildId]|CONTAINER_SKIP|container=$containerId|type=$actionType|task=${task.taskName}"
                    )
                    pipelineRuntimeService.updateTaskStatus(
                        buildId = buildId, taskId = task.taskId, userId = task.starter, buildStatus = BuildStatus.SKIP
                    )
                    pipelineBuildDetailService.taskEnd(
                        buildId = buildId, taskId = task.taskId, buildStatus = BuildStatus.SKIP
                    )
                    waitToDoTask = null

                    LogUtils.addYellowLine(
                        rabbitTemplate = rabbitTemplate,
                        buildId = task.buildId,
                        message = "插件[${task.taskName}]被跳过",
                        tag = task.taskId,
                        jobId = task.containerHashId,
                        executeCount = task.executeCount ?: 1
                    )
                    return@nextOne
                } else {
                    containerFinalStatus = BuildStatus.RUNNING
                    return Triple(waitToDoTask, containerFinalStatus, startVMFail)
                }
            } else if (BuildStatus.isFailure(task.status) && !continueWhenFailure(task.additionalOptions)) {
                // 如果在待执行插件之前前面还有失败的插件，则整个设置状态失败，因为即使重试也是失败了。
                containerFinalStatus = task.status
                if (waitToDoTask == null) {
                    startVMFail = task.taskSeq == 0
                    return Triple(waitToDoTask, containerFinalStatus, startVMFail)
                }
            } else if (BuildStatus.isFailure(task.status) && continueWhenFailure(task.additionalOptions)) {
                hasFailedTaskInSuccessContainer = true
            }
        }
        return Triple(waitToDoTask, containerFinalStatus, startVMFail)
    }

    fun checkIfAllSkip(
        buildId: String,
        stageId: String,
        container: PipelineBuildContainer,
        containerTaskList: Collection<PipelineBuildTask>,
        variables: Map<String, Any>
    ): Boolean {

        val containerId = container.containerId
        val containerControlOption = container.controlOption ?: return false
        val jobControlOption = containerControlOption.jobControlOption

        var skip = !jobControlOption.enable
        if (skip) {
            logger.info("[$buildId]|CONTAINER_DISABLE|stageId=$stageId|containerId=$containerId|enable=false")
            return skip
        }

        if (jobControlOption.runCondition == JobRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN ||
            jobControlOption.runCondition == JobRunCondition.CUSTOM_VARIABLE_MATCH
        ) {
            val conditions = jobControlOption.customVariables ?: emptyList()
            skip = ControlUtils.checkJobSkipCondition(conditions, variables, buildId, jobControlOption.runCondition)
        }

        if (skip) {
            logger.info("[$buildId]|CONDITION_SKIP|stageId=$stageId|containerId=$containerId|conditions=$jobControlOption")
            return skip
        }

        skip = true
        run manualSkip@{
            containerTaskList.forEach next@{ task ->
                // 环境控制类的任务不参与判断
                if (task.taskType == EnvControlTaskType.NORMAL.name || task.taskType == EnvControlTaskType.VM.name) {
                    return@next
                }
                if (task.status != BuildStatus.SKIP) { // 没有指定跳过
                    if (ControlUtils.isEnable(task.additionalOptions)) { // 插件是启用的，则不跳过
                        skip = false
                        return@manualSkip
                    }
                }
            }
        }

        if (skip) {
            logger.info("[$buildId]|CONTAINER_MANUAL_SKIP|stageId=$stageId|container=$containerId|skipped")
        }

        return skip
    }

    private fun PipelineBuildContainerEvent.sendTask(waitToDoTask: PipelineBuildTask, actionType: ActionType) {
        logger.info("[$buildId]|CONTAINER_$actionType|stage=$stageId|container=$containerId|task=${waitToDoTask.taskName}")
        pipelineEventDispatcher.dispatch(
            PipelineBuildAtomTaskEvent(
                source = "CONTAINER_$actionType",
                projectId = waitToDoTask.projectId,
                pipelineId = waitToDoTask.pipelineId,
                userId = userId,
                buildId = waitToDoTask.buildId,
                stageId = waitToDoTask.stageId,
                containerId = waitToDoTask.containerId,
                containerType = waitToDoTask.containerType,
                taskId = waitToDoTask.taskId,
                taskParam = waitToDoTask.taskParams,
                actionType = actionType
            )
        )
    }

    private fun PipelineBuildContainerEvent.sendBackStage(source: String) {
        pipelineEventDispatcher.dispatch(
            PipelineBuildStageEvent(
                source = source,
                projectId = projectId,
                pipelineId = pipelineId,
                userId = userId,
                buildId = buildId,
                stageId = stageId,
                actionType = ActionType.REFRESH
            )
        )
    }

    // 自己延时自己
    private fun PipelineBuildContainerEvent.sendSelfDelay() {
        pipelineEventDispatcher.dispatch(
            PipelineBuildContainerEvent(
                source = "CONTAINER_MUTEX_DELAY",
                projectId = projectId,
                pipelineId = pipelineId,
                userId = userId,
                buildId = buildId,
                stageId = stageId,
                containerId = containerId,
                containerType = containerType,
                actionType = actionType,
                delayMills = 10000 // 延时10秒钟
            )
        )
    }
}
