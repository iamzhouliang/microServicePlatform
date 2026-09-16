<script lang="ts" setup>
/**
 * 代码生成表字段配置页面
 * 用于管理表字段的代码生成配置，支持行内编辑模式
 */
import { onMounted } from 'vue';

import { useFs } from '@fast-crud/fast-crud';
import { message } from 'ant-design-vue';

import * as api from './api';
import createCrudOptions from './crud';

const { crudRef, crudBinding, crudExpose } = useFs({
  createCrudOptions,
});

/** 保存编辑的字段配置 */
const save = async () => {
  const res = await crudExpose.editable.validate();
  if (res !== true) {
    message.error(`校验失败：${JSON.stringify(res)}`);
    return;
  }
  await api.BatchUpdate(crudBinding.value?.data || []);
  message.success('保存成功');
  await crudExpose.doRefresh();
};

/** 复原数据，放弃编辑 */
const editRestore = async () => {
  await crudExpose.doRefresh();
};

onMounted(() => {
  crudExpose.doRefresh();
});
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding">
      <template #cell_name="scope">
        <a-tooltip :title="scope.row.name" placement="top">
          {{ scope.row.name }}
        </a-tooltip>
      </template>
      <template #cell_tableName="scope">
        <a-tooltip :title="scope.row.tableName" placement="top">
          {{ scope.row.tableName }}
        </a-tooltip>
      </template>

      <template v-if="crudBinding" #actionbar-right>
        <a-radio-group
          v-if="crudBinding.table && crudBinding.table.editable"
          v-model:value="crudBinding.table.editable.enabled"
          class="ml-5"
        >
          <a-radio-button :value="true">启用编辑</a-radio-button>
          <a-radio-button :value="false">退出编辑</a-radio-button>
        </a-radio-group>

        <template
          v-if="
            crudBinding.table &&
            crudBinding.table.editable &&
            crudBinding.table.editable.enabled
          "
        >
          <fs-button class="ml-5" @click="save">保存</fs-button>
        </template>
        <template v-else>
          <fs-button class="ml-5" @click="editRestore">复原</fs-button>
        </template>
      </template>
    </fs-crud>
  </fs-page>
</template>
