<script lang="ts" setup>
/**
 * 代码生成表配置页面
 * 用于管理数据库表的代码生成配置
 */
import { onMounted, ref } from 'vue';

import { useFs } from '@fast-crud/fast-crud';

import DsTableList from '../components/ds-table-list.vue';
import PreviewCode from '../components/preview-code.vue';
import createCrudOptions from './crud';

/** 数据源表选择弹窗可见性 */
const isModalVisible = ref(false);
/** 代码预览弹窗可见性 */
const isModalVisiblePre = ref(false);
/** 当前预览的表ID */
const previewId = ref('');

/** 打开数据源表选择弹窗 */
const showModal = () => {
  isModalVisible.value = true;
};

/** 打开代码预览弹窗 */
const showModalPre = (tableId: string) => {
  previewId.value = tableId;
  isModalVisiblePre.value = true;
};

const { crudRef, crudBinding, crudExpose } = useFs({
  createCrudOptions,
  context: { showModalPre },
});

onMounted(() => {
  crudExpose.doRefresh();
});
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding">
      <template #actionbar-left>
        <a-button type="primary" @click="showModal"> 添加配置 </a-button>
      </template>
      <template #cell_rootDir="scope">
        <a-tooltip :title="scope.row.rootDir" placement="top">
          {{ scope.row.rootDir }}
        </a-tooltip>
      </template>
      <template #cell_parentPackage="scope">
        <a-tooltip :title="scope.row.parentPackage" placement="top">
          {{ scope.row.parentPackage }}
        </a-tooltip>
      </template>
    </fs-crud>
    <DsTableList
      v-model:visible="isModalVisible"
      @success="crudExpose.doRefresh"
    />
    <PreviewCode v-model:visible="isModalVisiblePre" :pre-id="previewId" />
  </fs-page>
</template>
