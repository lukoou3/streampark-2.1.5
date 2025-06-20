<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<script lang="ts" setup name="FlinkEnvDraw">
  import { ref, reactive } from 'vue';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { SyncOutlined } from '@ant-design/icons-vue';
  import { useMonaco } from '/@/hooks/web/useMonaco';
  import { FlinkEnv } from '/@/api/flink/setting/types/flinkEnv.type';
  import { fetchFlinkInfo, fetchFlinkSync } from '/@/api/flink/setting/flinkEnv';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { useI18n } from '/@/hooks/web/useI18n';

  const { t } = useI18n();
  const flinkInfo = reactive<Recordable>({});
  const conf = ref();
  const syncLoading = ref(false);
  const { Swal } = useMessage();
  const [registerDrawer] = useDrawerInner((data: FlinkEnv) => {
    Object.assign(flinkInfo, data);
    setContent(flinkInfo.flinkConf);
    const height = document.documentElement.offsetHeight || document.body.offsetHeight;
    conf.value.style.height = height - 150 + 'px';
  });

  const { setContent } = useMonaco(conf, {
    language: 'yaml',
    options: {
      selectOnLineNumbers: false,
      foldingStrategy: 'indentation', // code fragmentation
      overviewRulerBorder: false, // Don't scroll bar borders
      tabSize: 2, // tab indent length
      readOnly: true,
      scrollBeyondLastLine: false,
      lineNumbersMinChars: 5,
      lineHeight: 24,
      automaticLayout: true,
      cursorStyle: 'line',
      cursorWidth: 3,
      renderFinalNewline: 'on',
      renderLineHighlight: 'all',
      quickSuggestionsDelay: 100, // Code prompt delay
      minimap: { enabled: true },
      scrollbar: {
        useShadows: false,
        vertical: 'visible',
        horizontal: 'visible',
        horizontalSliderSize: 5,
        verticalSliderSize: 5,
        horizontalScrollbarSize: 15,
        verticalScrollbarSize: 15,
      },
    },
  });
  /* Sync configuration */
  async function handleSync() {
    try {
      syncLoading.value = true;
      await fetchFlinkSync(flinkInfo.id);
      const flinkResult = await fetchFlinkInfo(flinkInfo.id);
      Object.assign(flinkInfo, flinkResult);
      setContent(flinkInfo.flinkConf);
      Swal.fire({
        icon: 'success',
        title: flinkResult.flinkName.concat(' conf sync successful!'),
        showConfirmButton: false,
        timer: 2000,
      });
    } catch (error) {
      console.error(error);
    } finally {
      syncLoading.value = false;
    }
  }
</script>
<template>
  <BasicDrawer @register="registerDrawer" width="40%" placement="right">
    <template #title> Flink Conf </template>
    <div>
      <div>
        <div ref="conf"></div>
        <a-button type="primary" class="mt-20px ml-20px" @click="handleSync" :loading="syncLoading">
          <SyncOutlined />
          {{ t('setting.flinkHome.sync') }}
        </a-button>
      </div>
    </div>
  </BasicDrawer>
</template>
