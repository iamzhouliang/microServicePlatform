<script lang="ts" setup>
/**
 * 代码生成模板管理页面
 * 用于管理代码生成模板（如 Entity、Controller、Service 等）
 */
import { onMounted, ref } from 'vue';

import { useFs } from '@fast-crud/fast-crud';

import TemplateEditor from '../components/template-editor.vue';
import * as api from './api';
import createCrudOptions from './crud';

/** 编辑器弹窗可见性 */
const isModalVisible = ref(false);
/** 是否为查看模式 */
const isViewEditor = ref(false);
/** 当前编辑的模板ID */
const currentTemplateId = ref<null | string>(null);

/** 打开新增模板弹窗 */
const showAddModal = () => {
  currentTemplateId.value = null;
  isModalVisible.value = true;
};

/** 打开编辑模板弹窗 */
const showEditModal = (templateId: string) => {
  currentTemplateId.value = templateId;
  isViewEditor.value = false;
  isModalVisible.value = true;
};

/** 打开查看模板弹窗 */
const showViewModal = (templateId: string) => {
  currentTemplateId.value = templateId;
  isViewEditor.value = true;
  isModalVisible.value = true;
};

/** 关闭弹窗 */
const handleClose = () => {
  isModalVisible.value = false;
  isViewEditor.value = false;
};

const { crudRef, crudBinding, crudExpose } = useFs({
  createCrudOptions,
  context: { showEditModal, showViewModal },
});

/** 保存新模板 */
const handleSave = async (val: any) => {
  await api.AddObj(val);
  crudExpose.doRefresh();
  handleClose();
};

/** 更新已有模板 */
const handleEdit = async (row: any) => {
  await api.UpdateObj(row);
  crudExpose.doRefresh();
  handleClose();
};

onMounted(() => {
  crudExpose.doRefresh();
});
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding">
      <template #actionbar-left>
        <a-button type="primary" @click="showAddModal"> 添加配置 </a-button>
      </template>
      <template #cell_generatePath="scope">
        <a-tooltip :title="scope.row.generatePath" placement="top">
          {{ scope.row.generatePath }}
        </a-tooltip>
      </template>
    </fs-crud>
    <TemplateEditor
      v-model:visible="isModalVisible"
      :template-id="currentTemplateId"
      :is-view-mode="isViewEditor"
      @close="handleClose"
      @edit="handleEdit"
      @save="handleSave"
    />
  </fs-page>
</template>
