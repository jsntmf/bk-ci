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
package com.tencent.devops.store.resources.image.user

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.store.api.image.user.UserMarketImageMemberResource
import com.tencent.devops.store.pojo.common.StoreMemberItem
import com.tencent.devops.store.pojo.common.StoreMemberReq
import com.tencent.devops.store.pojo.common.enums.StoreTypeEnum
import com.tencent.devops.store.service.image.ImageMemberService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class UserMarketImageMemberResourceImpl @Autowired constructor(
    private val imageMemberService: ImageMemberService
) : UserMarketImageMemberResource {
    override fun view(userId: String, imageCode: String): Result<StoreMemberItem?> {
        return imageMemberService.viewMemberInfo(userId, imageCode, StoreTypeEnum.IMAGE)
    }

    override fun list(userId: String, imageCode: String): Result<List<StoreMemberItem?>> {
        return imageMemberService.list(userId, imageCode, StoreTypeEnum.IMAGE)
    }

    override fun add(userId: String, storeMemberReq: StoreMemberReq): Result<Boolean> {
        return imageMemberService.add(userId, storeMemberReq, StoreTypeEnum.IMAGE)
    }

    override fun delete(userId: String, id: String, imageCode: String): Result<Boolean> {
        return imageMemberService.delete(userId, id, imageCode, StoreTypeEnum.IMAGE)
    }

    override fun changeMemberTestProjectCode(
        accessToken: String,
        userId: String,
        projectCode: String,
        imageCode: String
    ): Result<Boolean> {
        return imageMemberService.changeMemberTestProjectCode(
            accessToken = accessToken,
            userId = userId,
            projectCode = projectCode,
            storeCode = imageCode,
            storeType = StoreTypeEnum.IMAGE
        )
    }
}