/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
export default {
  title: 'Flink Home',
  conf: 'Flink Conf',
  sync: 'Sync Conf',
  edit: 'Edit Flink Home',
  delete: 'Are you sure delete this flink home ?',
  flinkName: 'Flink Name',
  flinkNamePlaceholder: 'Please enter flink name',
  flinkHome: 'Flink Home',
  flinkHomePlaceholder: 'Please enter flink home',
  flinkVersion: 'Flink Version',
  searchByName: 'Search by flink name',
  description: 'Description',
  descriptionPlaceholder: 'Please enter description',
  operateMessage: {
    flinkNameTips: 'The flink name, e.g: flink-1.12',
    flinkNameIsUnique: 'The flink name is already exists',
    flinkNameIsRequired: 'The flink name is required',
    flinkHomeTips: 'The absolute path of the FLINK_HOME',
    flinkHomeIsRequired: 'The flink home is required',
    createFlinkHomeSuccessful: ' Create successful!',
    updateFlinkHomeSuccessful: ' Update successful!',
    invalidPath: 'FLINK_HOME invalid path.',
    flinkHomeError: 'Can no found flink-dist or found multiple flink-dist, FLINK_HOME error.',
    deleteSuccess: 'The current flink home is removed.',
    setDefaultSuccess: `{name} is set as the default flink home.`,
  },
};
