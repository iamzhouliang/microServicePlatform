<script setup lang="ts">
import type { PageSchema } from 'epic-designer';

import { nextTick, ref, watch } from 'vue';

import { useUi } from '@fast-crud/fast-crud';
import { EDesigner } from 'epic-designer';

import * as api from './api';

defineOptions({ name: 'FormDesignModal' });

const props = defineProps<{
  modelId?: string;
  modelTitle?: string;
  open: boolean;
}>();

const emit = defineEmits<{
  success: [];
  'update:open': [value: boolean];
}>();

const { ui } = useUi();
const designerRef = ref<InstanceType<typeof EDesigner>>();
const loading = ref(false);

/** 监听弹窗打开，加载数据 */
watch(
  () => props.open,
  async (open) => {
    if (open && props.modelId) {
      loading.value = true;
      try {
        const data = await api.getOnlineModelDetail(props.modelId);
        await nextTick();
        if (data?.formSchemas) {
          designerRef.value?.setData({
            schemas: data.formSchemas,
            script: data.formScript,
          });
        }
      } finally {
        loading.value = false;
      }
    }
  },
);

/** 保存表单设计 */
async function handleSave(e: PageSchema) {
  if (!props.modelId) return;
  loading.value = true;
  try {
    await api.saveFormDesign(props.modelId, e);
    ui.notification.success('表单设计成功');
    emit('success');
    handleClose();
  } finally {
    loading.value = false;
  }
}

/** 关闭弹窗 */
function handleClose() {
  emit('update:open', false);
}
</script>

<template>
  <a-modal
    :open="props.open"
    :footer="null"
    :destroy-on-close="true"
    :keyboard="false"
    :mask-closable="false"
    width="100vw"
    wrap-class-name="form-design-modal"
    @cancel="handleClose"
  >
    <template #title>
      <span>{{ props.modelTitle || '表单设计' }}</span>
    </template>

    <a-spin :spinning="loading" tip="加载中..." class="designer-spin">
      <div class="form-designer">
        <EDesigner
          ref="designerRef"
          :disabled-zoom="true"
          :lock-default-schema-edit="true"
          title=""
          @save="handleSave"
        >
          <template #header-prefix>
            <div class="designer-logo">WP 设计器</div>
          </template>
        </EDesigner>
      </div>
    </a-spin>
  </a-modal>
</template>

<style lang="less">
/* 全屏弹窗样式 */
.form-design-modal {
  .ant-modal {
    top: 0 !important;
    max-width: 100vw !important;
    height: 100vh;
    padding: 0 !important;
    margin: 0 !important;
  }

  .ant-modal-content {
    display: flex;
    flex-direction: column;
    height: 100vh;
    padding: 0 !important;
    border-radius: 0;
  }

  .ant-modal-header {
    flex-shrink: 0;
    padding: 12px 16px;
    border-bottom: 1px solid #f0f0f0;
  }

  .ant-modal-body {
    flex: 1;
    min-height: 0;
    padding: 0 !important;
    overflow: hidden;
  }

  .ant-modal-close {
    top: 12px;
  }
}
</style>

<style scoped lang="less">
.designer-logo {
  padding: 0 12px;
  font-size: 14px;
  font-weight: 500;
  color: #1677ff;
}

.designer-spin {
  height: 100%;

  :deep(.ant-spin-nested-loading),
  :deep(.ant-spin-container) {
    height: 100%;
  }
}

.form-designer {
  height: 100%;
}
</style>
