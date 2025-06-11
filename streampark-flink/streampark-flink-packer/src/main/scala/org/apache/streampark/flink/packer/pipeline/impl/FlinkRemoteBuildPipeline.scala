/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.streampark.flink.packer.pipeline.impl

import org.apache.streampark.common.fs.LfsOperator
import org.apache.streampark.flink.packer.maven.MavenTool
import org.apache.streampark.flink.packer.pipeline._

/** Building pipeline for flink standalone session mode */
class FlinkRemoteBuildPipeline(request: FlinkRemotePerJobBuildRequest) extends BuildPipeline {

  override def pipeType: PipelineType = PipelineType.FLINK_STANDALONE

  override def offerBuildParam: FlinkRemotePerJobBuildRequest = request

  /**
   * job构建：
   *   1.清空workspace目录(先删除目录再创建目录)
   *   2.把执行程序和依赖都打成一个fat包，并存放在workspace目录
   * 请求job构建的调用栈：
   * [[ApplicationBuildPipelineController.buildApplication]]: http请求调用controller buildApplication方法，直接调用service buildApplication方法
   * [[ApplicationServiceImpl.buildApplication]]: 检查flink环境，检查是否需要build，调用appBuildPipeService.buildApplication
   * [[AppBuildPipeServiceImpl.buildApplication]]: FlinkRemoteBuildPipeline(FlinkRemotePerJobBuildRequest) pipeline，然后异步调用pipeline.launch
   * [[AppBuildPipeServiceImpl.buildApplication]]: FlinkRemoteBuildPipeline(FlinkRemotePerJobBuildRequest) pipeline，然后异步调用pipeline.launch
   * [[BuildPipeline.launch]]: 启动building pipeline，就只是异步调用buildProcess()方法
   * [[FlinkRemoteBuildPipeline.buildProcess]]: 构建过程。1.清空workspace目录(先删除目录再创建目录)，2.把执行程序和依赖都打成一个fat包，并存放在workspace目录
   *
   * The construction logic needs to be implemented by subclasses
   */
  @throws[Throwable]
  override protected def buildProcess(): ShadedBuildResponse = {
    execStep(1) {
      /**
       * 清空workspace目录：先删除目录再创建目录
       * 例如：/opt/module/streampark_data/workspace/100002
       */
      LfsOperator.mkCleanDirs(request.workspace)
      logInfo(s"recreate building workspace: ${request.workspace}")
    }.getOrElse(throw getError.exception)
    /**
     * build flink job shaded jar
     * 把执行程序和依赖都打成一个fat包，并存放在一个目录
     * 例如：/opt/module/streampark_data/workspace/100002/streampark-flinkjob_FlinkEtlDemo.jar
     */
    val shadedJar =
      execStep(2) {
        // 把执行程序和依赖都打成一个fat包
        val output = MavenTool.buildFatJar(
          request.mainClass,
          request.providedLibs,
          request.getShadedJarPath(request.workspace))
        logInfo(s"output shaded flink job jar: ${output.getAbsolutePath}")
        output
      }.getOrElse(throw getError.exception)
    ShadedBuildResponse(request.workspace, shadedJar.getAbsolutePath)
  }

}

object FlinkRemoteBuildPipeline {
  def of(request: FlinkRemotePerJobBuildRequest): FlinkRemoteBuildPipeline =
    new FlinkRemoteBuildPipeline(request)
}
