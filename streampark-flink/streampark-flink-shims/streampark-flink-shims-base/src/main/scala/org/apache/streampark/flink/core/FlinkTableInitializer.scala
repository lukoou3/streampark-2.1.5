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
package org.apache.streampark.flink.core

import org.apache.streampark.common.conf.ConfigConst._
import org.apache.streampark.common.enums.{ApiType, PlannerType}
import org.apache.streampark.common.enums.ApiType.ApiType
import org.apache.streampark.common.util.{DeflaterUtils, PropertiesUtils}
import org.apache.streampark.flink.core.EnhancerImplicit._

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.table.api.{EnvironmentSettings, TableConfig, TableEnvironment}
import org.apache.flink.table.api.bridge.scala.StreamTableEnvironment

import scala.collection.JavaConversions._
import scala.collection.Map
import scala.util.{Failure, Success, Try}

private[flink] object FlinkTableInitializer {

  def initialize(
      args: Array[String],
      config: (TableConfig, ParameterTool) => Unit): (ParameterTool, TableEnvironment) = {
    val flinkInitializer = new FlinkTableInitializer(args, ApiType.scala)
    flinkInitializer.tableConfFunc = config
    (flinkInitializer.configuration.parameter, flinkInitializer.tableEnv)
  }

  def initialize(args: TableEnvConfig): (ParameterTool, TableEnvironment) = {
    val flinkInitializer = new FlinkTableInitializer(args.args, ApiType.java)
    flinkInitializer.javaTableEnvConfFunc = args.conf
    (flinkInitializer.configuration.parameter, flinkInitializer.tableEnv)
  }

  def initialize(
      args: Array[String],
      configStream: (StreamExecutionEnvironment, ParameterTool) => Unit,
      configTable: (TableConfig, ParameterTool) => Unit)
      : (ParameterTool, StreamExecutionEnvironment, StreamTableEnvironment) = {

    val flinkInitializer = new FlinkTableInitializer(args, ApiType.scala)
    flinkInitializer.streamEnvConfFunc = configStream
    flinkInitializer.tableConfFunc = configTable
    (
      flinkInitializer.configuration.parameter,
      flinkInitializer.streamEnv,
      flinkInitializer.streamTableEnv)
  }

  def initialize(args: StreamTableEnvConfig)
      : (ParameterTool, StreamExecutionEnvironment, StreamTableEnvironment) = {
    val flinkInitializer = new FlinkTableInitializer(args.args, ApiType.java)
    flinkInitializer.javaStreamEnvConfFunc = args.streamConfig
    flinkInitializer.javaTableEnvConfFunc = args.tableConfig
    (
      flinkInitializer.configuration.parameter,
      flinkInitializer.streamEnv,
      flinkInitializer.streamTableEnv)
  }

}

private[flink] class FlinkTableInitializer(args: Array[String], apiType: ApiType)
  extends FlinkStreamingInitializer(args, apiType) {

  private[this] lazy val envSettings = {

    val builder = EnvironmentSettings.newInstance()

    Try(PlannerType.withName(parameter.get(KEY_FLINK_TABLE_PLANNER)))
      .getOrElse(PlannerType.blink) match {
      case PlannerType.blink =>
        val useBlinkPlanner =
          Try(builder.getClass.getDeclaredMethod("useBlinkPlanner")).getOrElse(null)
        if (useBlinkPlanner == null) {
          logWarn("useBlinkPlanner deprecated")
        } else {
          useBlinkPlanner.setAccessible(true)
          useBlinkPlanner.invoke(builder)
          logInfo("blinkPlanner will be use.")
        }
      case PlannerType.old =>
        val useOldPlanner = Try(builder.getClass.getDeclaredMethod("useOldPlanner")).getOrElse(null)
        if (useOldPlanner == null) {
          logWarn("useOldPlanner deprecated")
        } else {
          useOldPlanner.setAccessible(true)
          useOldPlanner.invoke(builder)
          logInfo("useOldPlanner will be use.")
        }
      case PlannerType.any =>
        val useAnyPlanner = Try(builder.getClass.getDeclaredMethod("useAnyPlanner")).getOrElse(null)
        if (useAnyPlanner == null) {
          logWarn("useAnyPlanner deprecated")
        } else {
          logInfo("useAnyPlanner will be use.")
          useAnyPlanner.setAccessible(true)
          useAnyPlanner.invoke(builder)
        }
    }

    val buildWith =
      (parameter.get(KEY_FLINK_TABLE_CATALOG), parameter.get(KEY_FLINK_TABLE_DATABASE))
    buildWith match {
      case (x: String, y: String) if x != null && y != null =>
        logInfo(s"with built in catalog: $x")
        logInfo(s"with built in database: $y")
        builder.withBuiltInCatalogName(x)
        builder.withBuiltInDatabaseName(y)
      case (x: String, _) if x != null =>
        logInfo(s"with built in catalog: $x")
        builder.withBuiltInCatalogName(x)
      case (_, y: String) if y != null =>
        logInfo(s"with built in database: $y")
        builder.withBuiltInDatabaseName(y)
      case _ =>
    }
    builder
  }

  lazy val tableEnv: TableEnvironment = {
    logInfo(s"job working in batch mode")
    envSettings.inBatchMode()
    val tableEnv = TableEnvironment.create(envSettings.build()).setAppName
    apiType match {
      case ApiType.java if javaTableEnvConfFunc != null =>
        javaTableEnvConfFunc.configuration(tableEnv.getConfig, parameter)
      case ApiType.scala if tableConfFunc != null =>
        tableConfFunc(tableEnv.getConfig, parameter)
      case _ =>
    }
    tableEnv
  }

  lazy val streamTableEnv: StreamTableEnvironment = {
    logInfo(s"components should work in streaming mode")
    envSettings.inStreamingMode()
    val setting = envSettings.build()

    if (streamEnvConfFunc != null) {
      streamEnvConfFunc(streamEnv, parameter)
    }
    if (javaStreamEnvConfFunc != null) {
      javaStreamEnvConfFunc.configuration(streamEnv.getJavaEnv, parameter)
    }
    val streamTableEnv = StreamTableEnvironment.create(streamEnv, setting).setAppName
    apiType match {
      case ApiType.java if javaTableEnvConfFunc != null =>
        javaTableEnvConfFunc.configuration(streamTableEnv.getConfig, parameter)
      case ApiType.scala if tableConfFunc != null =>
        tableConfFunc(streamTableEnv.getConfig, parameter)
      case _ =>
    }
    streamTableEnv
  }

  /** In case of table SQL, the parameter conf is not required, it depends on the developer. */

  override def initParameter(): FlinkConfiguration = {
    val cliParameterTool = ParameterTool.fromArgs(args)
    val configMap = super.parseConfig(cliParameterTool)
    val flinkConfiguration = {
      if (!cliParameterTool.has(KEY_APP_CONF()) && configMap.isEmpty) {
        logWarn(
          "Usage: can't fond config, this config is not required, you can set \"--conf $path \" in main arguments")
        FlinkConfiguration(cliParameterTool, new Configuration(), new Configuration())
      } else {
        // config priority: explicitly specified priority > project profiles > system profiles
        val appFlinkConf = extractConfigByPrefix(configMap, KEY_FLINK_PROPERTY_PREFIX)
        val appConf = extractConfigByPrefix(configMap, KEY_APP_PREFIX)
        val tableConf = extractConfigByPrefix(configMap, KEY_FLINK_TABLE_PREFIX)
        val sqlConf = extractConfigByPrefix(configMap, KEY_SQL_PREFIX)

        val envConfig = Configuration.fromMap(appFlinkConf)
        val tableConfig = Configuration.fromMap(tableConf)

        val parameterTool = ParameterTool
          .fromSystemProperties()
          .mergeWith(ParameterTool.fromMap(appFlinkConf))
          .mergeWith(ParameterTool.fromMap(appConf))
          .mergeWith(ParameterTool.fromMap(tableConf))
          .mergeWith(ParameterTool.fromMap(sqlConf))
          .mergeWith(cliParameterTool)

        FlinkConfiguration(parameterTool, envConfig, tableConfig)
      }
    }

    if (cliParameterTool.has(KEY_FLINK_SQL())) {
      // for streampark-console
      Try(DeflaterUtils.unzipString(cliParameterTool.get(KEY_FLINK_SQL()))) match {
        case Success(value) =>
          flinkConfiguration.copy(parameter = flinkConfiguration.parameter.mergeWith(
            ParameterTool.fromMap(Map(KEY_FLINK_SQL() -> value))))
        case Failure(e) =>
          new IllegalArgumentException(s"[StreamPark] --sql parsing failed. $e")
          flinkConfiguration
      }
    } else {
      flinkConfiguration
    }
  }

}
