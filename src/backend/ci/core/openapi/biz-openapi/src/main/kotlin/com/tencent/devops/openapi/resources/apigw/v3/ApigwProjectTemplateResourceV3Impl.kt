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
package com.tencent.devops.openapi.resources.apigw.v3

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.openapi.api.apigw.v3.ApigwProjectTemplateResourceV3
import com.tencent.devops.process.api.template.ServiceTemplateInstanceResource
import com.tencent.devops.process.api.template.ServiceTemplateResource
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.TemplateListModel
import com.tencent.devops.process.pojo.template.TemplateModelDetail
import com.tencent.devops.process.pojo.template.OptionalTemplateList
import com.tencent.devops.process.pojo.template.TemplateInstanceCreate
import com.tencent.devops.process.pojo.template.TemplateOperationRet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ApigwProjectTemplateResourceV3Impl @Autowired constructor(private val client: Client) : ApigwProjectTemplateResourceV3 {
    override fun listTemplate(
        appCode: String?,
        apigwType: String?,
        userId: String,
        projectId: String,
        templateType: TemplateType?,
        storeFlag: Boolean?
    ): Result<TemplateListModel> {
        logger.info("get project's pipeline all template, projectId($projectId) by user $userId")
        return client.get(ServiceTemplateResource::class).listTemplate(
            userId = userId,
            projectId = projectId,
            templateType = templateType,
            storeFlag = storeFlag
        )
    }

    override fun getTemplate(
        appCode: String?,
        apigwType: String?,
        userId: String,
        projectId: String,
        templateId: String,
        version: Long?
    ): Result<TemplateModelDetail> {
        logger.info("get project's pipeline template, projectId($projectId) templateId($templateId) version($version) by $userId")
        return client.get(ServiceTemplateResource::class).getTemplate(
            userId = userId,
            projectId = projectId,
            templateId = templateId,
            version = version
        )
    }

    override fun listAllTemplate(
        appCode: String?,
        apigwType: String?,
        userId: String,
        projectId: String
    ): Result<OptionalTemplateList> {
        logger.info("get project's pipeline all template, projectId($projectId) by user $userId")
        return client.get(ServiceTemplateResource::class).listAllTemplate(
            userId = userId,
            projectId = projectId,
            templateType = null
        )
    }

    override fun createTemplateInstances(
        appCode: String?,
        apigwType: String?,
        userId: String,
        projectId: String,
        templateId: String,
        version: Long,
        useTemplateSettings: Boolean,
        instances: List<TemplateInstanceCreate>
    ): TemplateOperationRet {
        logger.info("create TemplateInstances :userId=$userId,projectId=$projectId,templateId:$templateId,version:$version,useTemplateSettings:$useTemplateSettings,instances:$instances")
        return client.get(ServiceTemplateInstanceResource::class).createTemplateInstances(
                userId = userId,
                projectId = projectId,
                templateId = templateId,
                version = version,
                useTemplateSettings = useTemplateSettings,
                instances = instances)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ApigwProjectTemplateResourceV3Impl::class.java)
    }
}