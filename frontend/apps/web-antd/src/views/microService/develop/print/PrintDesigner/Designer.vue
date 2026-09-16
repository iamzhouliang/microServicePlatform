<script setup lang="ts">
/**
 * 打印设计器主组件
 * 封装 vue-plugin-hiprint 提供统一的 API
 */
import { onMounted, ref, shallowRef } from 'vue';
import {
  defaultElementTypeProvider,
  hiprint,
  hiPrintPlugin,
} from 'vue-plugin-hiprint';

import { useLocalStorage } from '@vueuse/core';
import { message } from 'ant-design-vue';

import DesignerToolbar from './components/DesignerToolbar.vue';
import PrintPreviewModal from './components/PrintPreviewModal.vue';
import LeftPanel from './left-layout.vue';
import printData from './print-data';
import providers from './providers';

defineOptions({ name: 'PrintDesigner' });

const props = withDefaults(
  defineProps<{
    /** 存储 key */
    storageKey?: string;
  }>(),
  {
    storageKey: 'KEY_TEMPLATES',
  },
);

const emit = defineEmits<{
  save: [data: any];
}>();

// 禁用自动连接打印机
hiPrintPlugin.disAutoConnect();

// 初始化 hiprint
hiprint.init({
  providers: [defaultElementTypeProvider()],
});

/** 模板实例 */
const hiprintTemplate = shallowRef<any>(null);
const previewRef = ref<InstanceType<typeof PrintPreviewModal>>();

/** 纸张尺寸 */
const paperWidth = ref(210);
const paperHeight = ref(296.6);

/** 缩放比例 */
const scale = ref(1);

/** 当前模板索引 */
const currentProviderIndex = ref(0);

/** 左侧面板模式 */
const leftPanelMode = ref<'local' | 'platform'>('local');
const leftPanelOptions = [
  { label: '本地控件', value: 'local' },
  { label: '平台控件', value: 'platform' },
];

/** 构建左侧可拖拽元素 */
function buildDraggableElements() {
  // @ts-expect-error jQuery 全局变量
  hiprint.PrintElementTypeManager.buildByHtml($('.ep-draggable-item'));
}

/** 构建设计器 */
function buildDesigner() {
  const container = document.querySelector('#hiprint-printTemplate');
  if (container) {
    container.innerHTML = '';
  }

  // 加载保存的模板数据
  const templates = useLocalStorage<Record<string, any>>(props.storageKey, {});
  const provider = providers[currentProviderIndex.value];
  const templateData = provider ? (templates.value[provider.value] ?? {}) : {};

  hiprintTemplate.value = new hiprint.PrintTemplate({
    template: templateData,
    dataMode: 1,
    history: false,
    onDataChanged: (_type: string, _json: any) => {},
    settingContainer: '#PrintElementOptionSetting',
    paginationContainer: '.hiprint-printPagination',
  });

  hiprintTemplate.value.design('#hiprint-printTemplate');
  scale.value = hiprintTemplate.value.editingPanel?.scale ?? 1;
}

/** 切换模板 */
function changeProvider(index: number) {
  currentProviderIndex.value = index;
  const provider = providers[index];
  if (!provider) return;

  hiprint.init({
    providers: [provider.f],
  });

  const epContainer = document.querySelector('.hiprintEpContainer');
  if (epContainer) {
    epContainer.innerHTML = '';
  }
  hiprint.PrintElementTypeManager.build('.hiprintEpContainer', provider.value);
  buildDesigner();
}

/** 设置纸张 */
function handleSetPaper(
  _type: string,
  size: { height: number; width: number },
) {
  paperWidth.value = size.width;
  paperHeight.value = size.height;
  hiprintTemplate.value?.setPaper(size.width, size.height);
}

/** 缩放 */
function handleScaleChange(newScale: number) {
  scale.value = newScale;
  hiprintTemplate.value?.zoom(newScale);
}

/** 预览 */
function handlePreview() {
  previewRef.value?.open(hiprintTemplate.value, printData);
}

/** 打印 */
function handlePrint() {
  hiprintTemplate.value?.print(printData, { leftOffset: -1, topOffset: -1 });
}

/** 导出 PDF */
function handleExportPdf() {
  hiprintTemplate.value?.toPdf({}, '打印模板');
}

/** 清空 */
function handleClear() {
  hiprintTemplate.value?.clear();
  message.success('已清空画布');
}

/** 保存 */
function handleSave() {
  const templates = useLocalStorage<Record<string, any>>(props.storageKey, {});
  const provider = providers[currentProviderIndex.value];
  if (!provider) return;
  const json = hiprintTemplate.value?.getJson();
  templates.value[provider.value] = json;
  message.success('保存成功');
  emit('save', json);
}

/** 获取模板数据 */
function getTemplateData() {
  return hiprintTemplate.value?.getJson();
}

/** 设置模板数据 */
function setTemplateData(data: any) {
  hiprintTemplate.value?.setJson(data);
}

defineExpose({
  getTemplateData,
  setTemplateData,
  hiprintTemplate,
});

onMounted(() => {
  buildDraggableElements();
  changeProvider(0);
});
</script>

<template>
  <div class="print-designer">
    <!-- 工具栏 -->
    <DesignerToolbar
      :paper-height="paperHeight"
      :paper-width="paperWidth"
      :scale="scale"
      :scale-max="3"
      :scale-min="0.5"
      @clear="handleClear"
      @export-pdf="handleExportPdf"
      @preview="handlePreview"
      @print="handlePrint"
      @save="handleSave"
      @set-paper="handleSetPaper"
      @update:scale="handleScaleChange"
    >
      <!-- 左侧插槽：控件类型切换 -->
      <template #left>
        <a-segmented
          v-model:value="leftPanelMode"
          :options="leftPanelOptions"
        />
      </template>
    </DesignerToolbar>

    <!-- 主体区域 -->
    <div class="designer-body">
      <!-- 左侧面板：控件列表 -->
      <div class="designer-left">
        <LeftPanel :hiprint-template="hiprintTemplate" :mode="leftPanelMode" />
      </div>

      <!-- 中间：设计画布 -->
      <div class="designer-canvas">
        <div id="hiprint-printTemplate" class="hiprint-printTemplate"></div>
      </div>

      <!-- 右侧面板：属性设置 -->
      <div class="designer-right">
        <a-tabs default-active-key="props" size="small">
          <a-tab-pane key="props" tab="控件属性">
            <div id="PrintElementOptionSetting" class="property-panel"></div>
          </a-tab-pane>
          <a-tab-pane key="print" tab="打印设置">
            <a-form layout="vertical" class="print-settings-form">
              <a-form-item label="模板编号">
                <a-input placeholder="请输入模板编号" />
              </a-form-item>
              <a-form-item label="模板名称">
                <a-input placeholder="请输入模板名称" />
              </a-form-item>
            </a-form>
          </a-tab-pane>
        </a-tabs>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <PrintPreviewModal ref="previewRef" />
  </div>
</template>

<style scoped lang="less">
.print-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
}

.designer-body {
  display: flex;
  flex: 1;
  min-height: 0;
  border-top: 1px solid #e8e8e8;
}

.designer-left {
  flex-shrink: 0;
  width: 220px;
  overflow-y: auto;
  background: #fafafa !important;
  border-right: 1px solid #e8e8e8;
}

.designer-canvas {
  flex: 1;
  min-width: 0;
  padding: 16px;
  overflow: auto;
  background: #f0f2f5 !important;
}

.designer-right {
  flex-shrink: 0;
  width: 260px;
  padding: 12px;
  overflow-y: auto;
  background: #fafafa !important;
  border-left: 1px solid #e8e8e8;
}

.property-panel {
  min-height: 300px;
}

.print-settings-form {
  padding: 8px 0;
}

// hiprint 样式覆盖
:deep(.hiprint-printTemplate) {
  background: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
}

:deep(.hiprint-printElement-type > li > ul > li > a) {
  height: auto !important;
}
</style>
