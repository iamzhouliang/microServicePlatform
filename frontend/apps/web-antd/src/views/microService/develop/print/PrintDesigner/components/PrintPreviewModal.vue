<script setup lang="ts">
/**
 * 打印预览弹窗组件
 */
import { ref } from 'vue';

import { DownloadOutlined, PrinterOutlined } from '@ant-design/icons-vue';

defineOptions({ name: 'PrintPreviewModal' });

const visible = ref(false);
const loading = ref(false);
const previewWidth = ref(210);

let hiprintTemplateRef: any = null;
let printDataRef: any = null;

/** 打开预览弹窗 */
function open(hiprintTemplate: any, printData: any, width = 210) {
  visible.value = true;
  loading.value = true;
  previewWidth.value = hiprintTemplate.editingPanel?.width ?? width;
  hiprintTemplateRef = hiprintTemplate;
  printDataRef = printData;

  setTimeout(() => {
    const container = document.querySelector('#print-preview-content');
    if (container) {
      container.innerHTML = '';
      const html = hiprintTemplate.getHtml(printData);
      container.append(html);
    }
    loading.value = false;
  }, 300);
}

/** 关闭弹窗 */
function close() {
  visible.value = false;
}

/** 执行打印 */
function handlePrint() {
  hiprintTemplateRef?.print(
    printDataRef,
    {},
    {
      callback: () => {},
    },
  );
}

/** 导出 PDF */
function handleExportPdf() {
  hiprintTemplateRef?.toPdf({}, '打印预览');
}

defineExpose({ open, close });
</script>

<template>
  <a-modal
    v-model:open="visible"
    title="打印预览"
    :footer="null"
    :mask-closable="false"
    :width="`${previewWidth + 40}mm`"
    centered
    @cancel="close"
  >
    <a-spin :spinning="loading" tip="加载预览...">
      <div id="print-preview-content" class="preview-content"></div>
    </a-spin>

    <template #footer>
      <div class="preview-footer">
        <a-space>
          <a-button @click="handlePrint">
            <template #icon><PrinterOutlined /></template>
            打印
          </a-button>
          <a-button @click="handleExportPdf">
            <template #icon><DownloadOutlined /></template>
            导出PDF
          </a-button>
          <a-button @click="close">关闭</a-button>
        </a-space>
      </div>
    </template>
  </a-modal>
</template>

<style scoped lang="less">
.preview-content {
  min-height: 200px;
  padding: 16px;
  background: #f5f5f5;
  border-radius: 4px;

  :deep(.hiprint-printTemplate) {
    background: #fff;
    box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
  }
}

.preview-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid #e8e8e8;
}
</style>
