<script lang="ts" setup>
/**
 * 代码生成模板编辑器组件
 * 用于新增/编辑/查看代码生成模板
 */
import type { FormInstance } from 'ant-design-vue';

import { ref, watch } from 'vue';

import { Button, Form, Input, Modal } from 'ant-design-vue';

import * as api from '../template/api';
import CodeEditor from './code-editor.vue';

const props = defineProps<{
  isViewMode?: boolean;
  templateId?: null | string;
  visible: boolean;
}>();

const emit = defineEmits(['close', 'save', 'edit', 'update:visible']);

/** 表单初始值 */
const initialFormValue = {
  id: '',
  generatePath: '',
  name: '',
  description: '',
  code: '',
};

const form = ref({ ...initialFormValue });
const formRef = ref<FormInstance>();

/** 获取模板详情 */
const fetchTemplateData = async (templateId: string) => {
  try {
    const res = await api.GetDetail(templateId);
    form.value = {
      id: res.id,
      generatePath: res.generatePath,
      name: res.name,
      description: res.description,
      code: res.code,
    };
  } catch (error) {
    console.error('获取模板详情失败:', error);
  }
};

/** 保存模板 */
const handleSave = async () => {
  try {
    await formRef.value?.validate();
    if (props.templateId) {
      emit('edit', form.value);
    } else {
      emit('save', form.value);
    }
    form.value = { ...initialFormValue };
  } catch (error) {
    console.error('表单校验失败:', error);
  }
};

/** 取消操作 */
const handleCancel = () => {
  form.value = { ...initialFormValue };
  emit('close');
  emit('update:visible', false);
};

// 监听弹窗可见性变化
watch(
  () => props.visible,
  (newVal) => {
    if (newVal && props.templateId) {
      fetchTemplateData(props.templateId);
    } else if (newVal) {
      form.value = { ...initialFormValue };
    }
  },
);
</script>

<template>
  <Modal
    :open="visible"
    title="代码生成模板配置"
    :footer="null"
    width="80%"
    :destroy-on-close="true"
    style="top: 20px"
    @cancel="handleCancel"
  >
    <div class="editor-container">
      <div class="form-panel">
        <Form ref="formRef" :model="form" layout="vertical">
          <Form.Item
            label="模板路径"
            name="generatePath"
            :rules="[{ required: true, message: '请输入模板路径' }]"
          >
            <Input.TextArea
              v-model:value="form.generatePath"
              placeholder="请输入模板路径"
              :disabled="props.isViewMode"
            />
          </Form.Item>
          <Form.Item
            label="模板名称"
            name="name"
            :rules="[{ required: true, message: '请输入模板名称' }]"
          >
            <Input
              v-model:value="form.name"
              placeholder="请输入模板名称"
              :disabled="props.isViewMode"
            />
          </Form.Item>
          <Form.Item label="模板描述" name="description">
            <Input.TextArea
              v-model:value="form.description"
              placeholder="请输入模板描述"
              :disabled="props.isViewMode"
            />
          </Form.Item>
        </Form>
      </div>
      <div class="code-panel">
        <CodeEditor
          v-model:command="form.code"
          :read-only="props.isViewMode"
          height="100%"
        />
      </div>
    </div>
    <div class="footer-button">
      <Button v-if="!props.isViewMode" type="primary" @click="handleSave">
        提交
      </Button>
    </div>
  </Modal>
</template>

<style scoped>
.ant-modal-body {
  padding: 0;
}

.editor-container {
  display: flex;
  height: 75vh;
  overflow: hidden;
}

.form-panel {
  width: 30%;
  padding: 16px;
  overflow-y: auto;
  border-right: 1px solid #f0f0f0;
}

.code-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
}

.footer-button {
  padding: 10px 16px;
  text-align: right;
  border-top: 1px solid #f0f0f0;
}
</style>
