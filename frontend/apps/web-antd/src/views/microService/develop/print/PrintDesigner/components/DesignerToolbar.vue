<script setup lang="ts">
/**
 * 打印设计器工具栏组件
 * 包含纸张设置、缩放控制、操作按钮
 */
import { computed, ref } from 'vue';

import {
  ClearOutlined,
  DownloadOutlined,
  EyeOutlined,
  MinusOutlined,
  PlusOutlined,
  PrinterOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue';

defineOptions({ name: 'DesignerToolbar' });

const props = defineProps<{
  paperHeight: number;
  paperWidth: number;
  scale: number;
  scaleMax?: number;
  scaleMin?: number;
}>();

const emit = defineEmits<{
  clear: [];
  exportPdf: [];
  preview: [];
  print: [];
  save: [];
  setPaper: [type: string, size: { height: number; width: number }];
  'update:scale': [value: number];
}>();

/** 纸张类型配置 */
const paperTypes = {
  A3: { width: 420, height: 296.6 },
  A4: { width: 210, height: 296.6 },
  A5: { width: 210, height: 147.6 },
  B3: { width: 500, height: 352.6 },
  B4: { width: 250, height: 352.6 },
  B5: { width: 250, height: 175.6 },
};

/** 自定义纸张弹窗 */
const customPaperVisible = ref(false);
const customWidth = ref(220);
const customHeight = ref(80);

/** 当前纸张类型 */
const currentPaperType = computed(() => {
  for (const [key, value] of Object.entries(paperTypes)) {
    if (
      value.width === props.paperWidth &&
      value.height === props.paperHeight
    ) {
      return key;
    }
  }
  return 'custom';
});

/** 缩放百分比显示 */
const scalePercent = computed(() => `${Math.round(props.scale * 100)}%`);

/** 设置纸张 */
function handleSetPaper(type: string, size: { height: number; width: number }) {
  emit('setPaper', type, size);
}

/** 设置自定义纸张 */
function handleCustomPaper() {
  customPaperVisible.value = false;
  emit('setPaper', 'custom', {
    width: customWidth.value,
    height: customHeight.value,
  });
}

/** 缩放操作 */
function handleZoom(delta: number) {
  const newScale = Math.round((props.scale + delta) * 10) / 10;
  const min = props.scaleMin ?? 0.5;
  const max = props.scaleMax ?? 3;
  if (newScale >= min && newScale <= max) {
    emit('update:scale', newScale);
  }
}
</script>

<template>
  <div class="designer-toolbar">
    <!-- 最左侧：插槽（控件类型切换） -->
    <div class="toolbar-section">
      <slot name="left"></slot>
    </div>

    <a-divider type="vertical" class="toolbar-divider" />

    <!-- 纸张设置 -->
    <div class="toolbar-section">
      <span class="section-label">纸张:</span>
      <a-button-group size="small">
        <a-button
          v-for="(size, type) in paperTypes"
          :key="type"
          :type="currentPaperType === type ? 'primary' : 'default'"
          @click="handleSetPaper(type, size)"
        >
          {{ type }}
        </a-button>
        <a-popover
          v-model:open="customPaperVisible"
          title="自定义纸张尺寸(mm)"
          trigger="click"
          placement="bottom"
        >
          <template #content>
            <div class="custom-paper-form">
              <a-space direction="vertical" :size="12">
                <a-input-number
                  v-model:value="customWidth"
                  :min="50"
                  :max="1000"
                  addon-before="宽"
                  addon-after="mm"
                  style="width: 160px"
                />
                <a-input-number
                  v-model:value="customHeight"
                  :min="50"
                  :max="1000"
                  addon-before="高"
                  addon-after="mm"
                  style="width: 160px"
                />
                <a-button type="primary" block @click="handleCustomPaper">
                  确定
                </a-button>
              </a-space>
            </div>
          </template>
          <a-button
            :type="currentPaperType === 'custom' ? 'primary' : 'default'"
          >
            自定义
          </a-button>
        </a-popover>
      </a-button-group>
    </div>

    <!-- 中间：缩放控制 -->
    <div class="toolbar-section">
      <span class="section-label">缩放:</span>
      <a-button-group size="small">
        <a-button
          :disabled="scale <= (scaleMin ?? 0.5)"
          @click="handleZoom(-0.1)"
        >
          <template #icon><MinusOutlined /></template>
        </a-button>
        <a-button disabled style="min-width: 60px; cursor: default">
          {{ scalePercent }}
        </a-button>
        <a-button @click="handleZoom(0.1)" :disabled="scale >= (scaleMax ?? 3)">
          <template #icon><PlusOutlined /></template>
        </a-button>
      </a-button-group>
    </div>

    <!-- 右侧：操作按钮 -->
    <div class="toolbar-section toolbar-actions">
      <a-space :size="8">
        <a-button size="small" @click="emit('preview')">
          <template #icon><EyeOutlined /></template>
          预览
        </a-button>
        <a-button size="small" @click="emit('print')">
          <template #icon><PrinterOutlined /></template>
          打印
        </a-button>
        <a-button size="small" @click="emit('exportPdf')">
          <template #icon><DownloadOutlined /></template>
          导出PDF
        </a-button>
        <a-popconfirm
          title="确定要清空画布吗？"
          ok-text="确定"
          cancel-text="取消"
          @confirm="emit('clear')"
        >
          <a-button size="small" danger>
            <template #icon><ClearOutlined /></template>
            清空
          </a-button>
        </a-popconfirm>
        <a-button size="small" type="primary" @click="emit('save')">
          <template #icon><SaveOutlined /></template>
          保存
        </a-button>
      </a-space>
    </div>
  </div>
</template>

<style scoped lang="less">
.designer-toolbar {
  display: flex;
  flex-shrink: 0;
  gap: 16px;
  align-items: center;
  height: 48px;
  padding: 0 16px;
  background: #fafafa;
  border-radius: 6px 6px 0 0;
}

.toolbar-section {
  display: flex;
  gap: 8px;
  align-items: center;
}

.toolbar-divider {
  height: 24px;
  margin: 0;
}

.toolbar-actions {
  margin-left: auto;
}

.section-label {
  font-size: 13px;
  color: #666;
}

.custom-paper-form {
  padding: 8px 0;
}
</style>
