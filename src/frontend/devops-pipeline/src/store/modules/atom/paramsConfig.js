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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

export const STRING = 'STRING'
export const BOOLEAN = 'BOOLEAN'
export const ENUM = 'ENUM'
export const MULTIPLE = 'MULTIPLE'
export const SVN_TAG = 'SVN_TAG'
export const GIT_REF = 'GIT_REF'
export const CODE_LIB = 'CODE_LIB'
export const CONTAINER_TYPE = 'CONTAINER_TYPE'
export const ARTIFACTORY = 'ARTIFACTORY'
export const SUB_PIPELINE = 'SUB_PIPELINE'

function paramType (typeConst) {
    return type => type === typeConst
}

export const DEFAULT_PARAM = {
    [STRING]: {
        id: 'string',
        defaultValue: 'value',
        desc: '',
        type: STRING,
        typeDesc: 'string',
        required: true
    },
    [BOOLEAN]: {
        id: 'bool',
        defaultValue: true,
        desc: '',
        type: BOOLEAN,
        typeDesc: 'bool',
        required: true
    },
    [ENUM]: {
        id: 'enum',
        defaultValue: '',
        desc: '',
        type: ENUM,
        typeDesc: 'enum',
        options: [],
        required: true
    },
    [MULTIPLE]: {
        id: 'multiple',
        defaultValue: '',
        desc: '',
        options: [],
        type: MULTIPLE,
        typeDesc: 'multiple',
        required: true
    },
    [SVN_TAG]: {
        id: 'svntag',
        defaultValue: '',
        repoHashId: '',
        relativePath: '',
        desc: '',
        options: [],
        type: SVN_TAG,
        typeDesc: 'svntag',
        required: true
    },
    [GIT_REF]: {
        id: 'gitref',
        defaultValue: '',
        repoHashId: '',
        desc: '',
        options: [],
        type: GIT_REF,
        typeDesc: 'gitref',
        required: true
    },
    [CODE_LIB]: {
        id: 'codelib',
        defaultValue: '',
        scmType: 'CODE_GIT',
        desc: '',
        options: [],
        type: CODE_LIB,
        typeDesc: 'codelib',
        required: true
    },
    [CONTAINER_TYPE]: {
        id: 'buildResource',
        defaultValue: '',
        containerType: {
            os: 'LINUX',
            buildType: 'DOCKER'
        },
        desc: '',
        options: [],
        type: CONTAINER_TYPE,
        typeDesc: 'buildResource',
        required: true
    },
    [ARTIFACTORY]: {
        id: 'artifactory',
        defaultValue: '',
        desc: '',
        options: [],
        glob: '*',
        properties: {},
        type: ARTIFACTORY,
        typeDesc: 'artifactory',
        required: true
    },
    [SUB_PIPELINE]: {
        id: 'subPipeline',
        defaultValue: '',
        desc: '',
        options: [],
        type: SUB_PIPELINE,
        typeDesc: 'subPipeline',
        required: true
    }
}

export const PARAM_LIST = Object.keys(DEFAULT_PARAM).map(key => ({
    id: key,
    name: DEFAULT_PARAM[key].typeDesc
}))

export const ParamComponentMap = {
    [STRING]: 'VuexInput',
    [BOOLEAN]: 'EnumInput',
    [ENUM]: 'Selector',
    [MULTIPLE]: 'Selector',
    [SVN_TAG]: 'Selector',
    [GIT_REF]: 'Selector',
    [CODE_LIB]: 'Selector',
    [CONTAINER_TYPE]: 'Selector',
    [ARTIFACTORY]: 'Selector',
    [SUB_PIPELINE]: 'Selector'
}

export const BOOLEAN_LIST = [
    {
        value: true,
        label: true
    },
    {
        value: false,
        label: false
    }
]

export function getRepoOption (type = 'CODE_SVN') {
    return {
        url: `/repository/api/user/repositories/{projectId}/hasPermissionList?permission=USE&repositoryType=${type}&page=1&pageSize=500`,
        paramId: 'repositoryHashId',
        paramName: 'aliasName',
        searchable: true,
        hasAddItem: true
    }
}

export const CODE_LIB_OPTION = {
    paramId: 'aliasName',
    paramName: 'aliasName',
    searchable: true,
    hasAddItem: true
}

export const SUB_PIPELINE_OPTION = {
    url: '/process/api/user/pipelines/{projectId}/hasPermissionList?permission=EXECUTE&excludePipelineId={pipelineId}&limit=-1',
    paramId: 'pipelineName',
    paramName: 'pipelineName',
    searchable: true
}

export const CODE_LIB_TYPE = [
    {
        id: 'CODE_GIT',
        name: 'GIT'
    },
    {
        id: 'CODE_SVN',
        name: 'SVN'
    },
    {
        id: 'GITHUB',
        name: 'GITHUB'
    }
]

export const isStringParam = paramType(STRING)
export const isBooleanParam = paramType(BOOLEAN)
export const isEnumParam = paramType(ENUM)
export const isMultipleParam = paramType(MULTIPLE)
export const isSvnParam = paramType(SVN_TAG)
export const isGitParam = paramType(GIT_REF)
export const isCodelibParam = paramType(CODE_LIB)
export const isBuildResourceParam = paramType(CONTAINER_TYPE)
export const isArtifactoryParam = paramType(ARTIFACTORY)
export const isSubPipelineParam = paramType(SUB_PIPELINE)
