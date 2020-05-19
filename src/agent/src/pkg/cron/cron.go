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

package cron

import (
	"fmt"
	"github.com/astaxie/beego/logs"
	"io/ioutil"
	"os"
	"pkg/util"
	"pkg/util/systemutil"
	"strings"
	"time"
)

func CleanDumpFileJob(intervalInHours int, cleanBeforeInHours int) {
	TryCleanDumpFile(cleanBeforeInHours)
	for {
		now := time.Now()
		nextTime := now.Add(time.Hour * time.Duration(intervalInHours))
		// nextTime := now.Add(time.Second * 30)
		logs.Info("next clean time: ", util.FormatTime(nextTime))
		t := time.NewTimer(nextTime.Sub(now))
		<-t.C
		TryCleanDumpFile(cleanBeforeInHours)
	}
}

func TryCleanDumpFile(hoursBefore int) {
	logs.Info("clean jvm dump file starts")
	defer func() {
		if err := recover(); err != nil {
			logs.Error("remove jvm dump files error: ", err)
		}
	}()

	workDir := systemutil.GetWorkDir()
	logs.Info("clean dump file in " + workDir + " before " + util.FormatTime(time.Now().Add(time.Hour*time.Duration(hoursBefore*-1))))
	files, err := ioutil.ReadDir(workDir)
	if err != nil {
		logs.Error("read dir error: ", err.Error())
	}
	for _, file := range files {
		if !file.IsDir() && strings.HasPrefix(file.Name(), "hs_err_pid") &&
			int(time.Since(file.ModTime()).Hours()) > hoursBefore {
			fileFullName := workDir + "/" + file.Name()
			err = os.Remove(fileFullName)
			if err != nil {
				logs.Warn(fmt.Sprintf("remove file %s failed: ", fileFullName), err.Error())
			} else {
				logs.Info(fmt.Sprintf("file %s removed", fileFullName))
			}
		}
	}
	logs.Info("clean jvm dump file done")
}
