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

package com.tencent.devops.log.utils

import com.tencent.devops.common.log.Ansi
import com.tencent.devops.log.model.message.LogMessage
import com.tencent.devops.log.model.pojo.LogEvent
import com.tencent.devops.log.model.pojo.LogStatusEvent
import com.tencent.devops.log.model.pojo.enums.LogType
import com.tencent.devops.log.utils.LogDispatcher.dispatch
import org.springframework.amqp.rabbit.core.RabbitTemplate

object LogUtils {

    fun addLine(rabbitTemplate: RabbitTemplate, buildId: String, message: String, tag: String, jobId: String? = null, executeCount: Int) {
        dispatch(rabbitTemplate, genLogEvent(buildId, message, tag, jobId, LogType.LOG, executeCount))
    }

    fun addLines(rabbitTemplate: RabbitTemplate, buildId: String, logMessages: List<LogMessage>) {
        dispatch(rabbitTemplate, LogEvent(buildId, logMessages))
    }

    fun addFoldStartLine(
        rabbitTemplate: RabbitTemplate,
        buildId: String,
        groupName: String,
        tag: String,
        jobId: String? = null,
        executeCount: Int
    ) {
        dispatch(rabbitTemplate, genLogEvent(buildId, "##[group]$groupName", tag, jobId, LogType.LOG, executeCount))
    }

    fun addFoldEndLine(
        rabbitTemplate: RabbitTemplate,
        buildId: String,
        groupName: String,
        tag: String,
        jobId: String? = null,
        executeCount: Int
    ) {
        dispatch(rabbitTemplate, genLogEvent(buildId, "##[endgroup]$groupName", tag, jobId, LogType.LOG, executeCount))
    }

    fun addRangeStartLine(
        rabbitTemplate: RabbitTemplate,
        buildId: String,
        rangeName: String,
        tag: String,
        jobId: String? = null,
        executeCount: Int
    ) {
        dispatch(rabbitTemplate, genLogEvent(buildId, "[START] $rangeName", tag, jobId, LogType.START, executeCount))
    }

    fun addRangeEndLine(
        rabbitTemplate: RabbitTemplate,
        buildId: String,
        rangeName: String,
        tag: String,
        jobId: String? = null,
        executeCount: Int
    ) {
        dispatch(rabbitTemplate, genLogEvent(buildId, "[END] $rangeName", tag, jobId, LogType.END, executeCount))
    }

    fun addYellowLine(
        rabbitTemplate: RabbitTemplate,
        buildId: String,
        message: String,
        tag: String,
        jobId: String? = null,
        executeCount: Int
    ) =
        addLine(rabbitTemplate, buildId, Ansi().bold().fgYellow().a(message).reset().toString(), tag, jobId, executeCount)

    fun addRedLine(rabbitTemplate: RabbitTemplate, buildId: String, message: String, tag: String, jobId: String?, executeCount: Int) =
        addLine(rabbitTemplate, buildId, Ansi().bold().fgRed().a(message).reset().toString(), tag, jobId, executeCount)

    fun updateLogStatus(
        rabbitTemplate: RabbitTemplate,
        buildId: String,
        finished: Boolean,
        tag: String,
        jobId: String? = null,
        executeCount: Int?
    ) {
        dispatch(rabbitTemplate, LogStatusEvent(buildId, finished, tag, jobId ?: "", executeCount))
    }

    fun stopLog(rabbitTemplate: RabbitTemplate, buildId: String, tag: String, jobId: String?, executeCount: Int? = null) {
        updateLogStatus(rabbitTemplate, buildId, true, tag, jobId, executeCount)
    }

    private fun genLogEvent(
        buildId: String,
        message: String,
        tag: String,
        jobId: String? = null,
        logType: LogType,
        executeCount: Int
    ): LogEvent {
        val logs = listOf(LogMessage(message, System.currentTimeMillis(), tag, jobId ?: "", logType, executeCount))
        return LogEvent(buildId, logs)
    }
}
