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

import org.apache.streampark.common.conf.ConfigConst.KEY_FLINK_SQL
import org.apache.streampark.common.util.Logger
import org.apache.streampark.flink.core.SqlCommand._

import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.{Configuration, ExecutionOptions}
import org.apache.flink.table.api.TableEnvironment

import java.util
import java.util.concurrent.locks.ReentrantReadWriteLock

import scala.collection.mutable
import scala.util.Try

private[flink] object FlinkSqlExecutor extends Logger {

  private[this] val lock = new ReentrantReadWriteLock().writeLock

  // sql任务的入口
  private[streampark] def executeSql(
      sql: String,
      parameter: ParameterTool,
      context: TableEnvironment)(implicit callbackFunc: String => Unit = null): Unit = {

    // 从参数--sql中读取sql
    val flinkSql: String =
      if (StringUtils.isBlank(sql)) parameter.get(KEY_FLINK_SQL()) else parameter.get(sql)
    require(StringUtils.isNotBlank(flinkSql), "verify failed: flink sql cannot be empty")

    def callback(r: String): Unit = {
      callbackFunc match {
        case null => logInfo(r)
        case x => x(r)
      }
    }

    val runMode = parameter.get(ExecutionOptions.RUNTIME_MODE.key())

    var hasInsert = false
    // 使用statementSet可以同时运行多个insert语句
    val statementSet = context.createStatementSet()
    SqlCommandParser
      .parseSQL(flinkSql) // 切分sql的各个语句, 把每个sql语句解析为SqlCommandCall
      .foreach(
        x => {
          val args = if (x.operands.isEmpty) null else x.operands.head
          val command = x.command.name
          x.command match {
            // For display sql statement result information
            case SHOW_CATALOGS =>
              val catalogs = context.listCatalogs
              callback(s"$command: ${catalogs.mkString("\n")}")
            case SHOW_CURRENT_CATALOG =>
              val catalog = context.getCurrentCatalog
              callback(s"$command: $catalog")
            case SHOW_DATABASES =>
              val databases = context.listDatabases
              callback(s"$command: ${databases.mkString("\n")}")
            case SHOW_CURRENT_DATABASE =>
              val database = context.getCurrentDatabase
              callback(s"$command: $database")
            case SHOW_TABLES =>
              val tables = context.listTables().filter(!_.startsWith("UnnamedTable"))
              callback(s"$command: ${tables.mkString("\n")}")
            case SHOW_FUNCTIONS =>
              val functions = context.listUserDefinedFunctions()
              callback(s"$command: ${functions.mkString("\n")}")
            case SHOW_MODULES =>
              val modules = context.listModules()
              callback(s"$command: ${modules.mkString("\n")}")
            case DESC | DESCRIBE =>
              val schema = context.scan(args).getSchema
              val builder = new mutable.StringBuilder()
              builder.append("Column\tType\n")
              for (i <- 0 to schema.getFieldCount) {
                builder.append(
                  schema.getFieldName(i).get() + "\t" + schema.getFieldDataType(i).get() + "\n")
              }
              callback(builder.toString())
            case EXPLAIN =>
              val tableResult = context.executeSql(x.originSql)
              val r = tableResult.collect().next().getField(0).toString
              callback(r)
            // For specific statement, such as: SET/RESET/INSERT/SELECT
            // set修改config
            case SET =>
              val operand = x.operands(1)
              logInfo(s"$command: $args --> $operand")
              context.getConfig.getConfiguration.setString(args, operand)
            // 似乎是重置config
            case RESET | RESET_ALL =>
              val confDataField = classOf[Configuration].getDeclaredField("confData")
              confDataField.setAccessible(true)
              val confData = confDataField
                .get(context.getConfig.getConfiguration)
                .asInstanceOf[util.HashMap[String, AnyRef]]
              confData.synchronized {
                if (x.command == RESET) {
                  confData.remove(args)
                } else {
                  confData.clear()
                }
              }
              logInfo(s"$command: $args")
            case BEGIN_STATEMENT_SET | END_STATEMENT_SET =>
              logWarn(s"SQL Client Syntax: ${x.command.name} ")
            // insert语句
            case INSERT =>
              statementSet.addInsertSql(x.originSql)
              hasInsert = true
            case SELECT =>
              logError("StreamPark dose not support 'SELECT' statement now!")
              throw new RuntimeException("StreamPark dose not support 'select' statement now!")
            case DELETE | UPDATE =>
              if (runMode == "STREAMING") {
                throw new UnsupportedOperationException(
                  s"Currently, ${command.toUpperCase()} statement only supports in batch mode, " +
                    s"and it requires the target table connector implements the SupportsRowLevelDelete, " +
                    s"For more details please refer to: https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/table/sql/$command")
              }
            case _ =>
              try {
                lock.lock()
                // 其他的都是执行executeSql，创建表创建视图等
                val result = context.executeSql(x.originSql)
                logInfo(s"$command:$args")
              } finally {
                if (lock.isHeldByCurrentThread) {
                  lock.unlock()
                }
              }
          }
        })

    if (hasInsert) {
      statementSet.execute() match {
        case t if t != null =>
          Try(t.getJobClient.get.getJobID).getOrElse(null) match {
            case x if x != null => logInfo(s"jobId:$x")
            case _ =>
          }
        case _ =>
      }
    } else {
      logError("No 'INSERT' statement to trigger the execution of the Flink job.")
      throw new RuntimeException("No 'INSERT' statement to trigger the execution of the Flink job.")
    }

    logInfo(
      s"\n\n\n==============flinkSql==============\n\n $flinkSql\n\n============================\n\n\n")
  }

}
