<script lang="ts" setup>
import { computed, defineAsyncComponent, ref } from 'vue';

import { message, Spin } from 'ant-design-vue';

defineOptions({ name: 'FilePreview' });

const props = defineProps<{
  fileInfo: {
    category?: string;
    ext?: string;
    originalFilename?: string;
    url?: string;
  };
  open: boolean;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
}>();

/** 按需加载 Office 预览组件 */
const VueOfficePdf = defineAsyncComponent({
  loader: () => import('@vue-office/pdf'),
  loadingComponent: Spin,
});

const VueOfficeDocx = defineAsyncComponent({
  loader: () => import('@vue-office/docx'),
  loadingComponent: Spin,
});

const VueOfficeExcel = defineAsyncComponent({
  loader: () => import('@vue-office/excel'),
  loadingComponent: Spin,
});

/** 文件类型判断 */
const WordTypes = new Set(['doc', 'docx']);
const ExcelTypes = new Set(['xls', 'xlsx']);
const VideoTypes = new Set(['mov', 'mp4', 'ogg', 'webm']);
const AudioTypes = new Set(['aac', 'flac', 'mp3', 'ogg', 'wav']);

const fileExt = computed(() => props.fileInfo?.ext?.toLowerCase() || '');

const previewType = computed(() => {
  const ext = fileExt.value;
  if (props.fileInfo?.category === 'IMAGE') return 'image';
  if (ext === 'pdf') return 'pdf';
  if (WordTypes.has(ext)) return 'word';
  if (ExcelTypes.has(ext)) return 'excel';
  if (VideoTypes.has(ext)) return 'video';
  if (AudioTypes.has(ext)) return 'audio';
  return 'unsupported';
});

/** Excel 配置 */
const excelConfig = ref({
  xls: false,
  minColLength: 0,
  minRowLength: 0,
});

const loading = ref(false);

function onRendered() {
  loading.value = false;
}

function onError() {
  loading.value = false;
  message.error('文件加载失败');
}

function onClose() {
  emit('update:open', false);
}
</script>

<template>
  <a-modal
    :open="props.open"
    :footer="null"
    :width="previewType === 'audio' ? 500 : 1200"
    :body-style="{ padding: '12px', maxHeight: '85vh', overflow: 'auto' }"
    :destroy-on-close="true"
    title="文件预览"
    @cancel="onClose"
  >
    <Spin :spinning="loading" tip="加载中...">
      <!-- 图片预览 -->
      <div v-if="previewType === 'image'" class="preview-container">
        <img
          :src="fileInfo?.url"
          :alt="fileInfo?.originalFilename"
          class="preview-image"
        />
      </div>

      <!-- PDF 预览 -->
      <VueOfficePdf
        v-else-if="previewType === 'pdf'"
        :src="fileInfo?.url"
        class="preview-office"
        @rendered="onRendered"
        @error="onError"
      />

      <!-- Word 预览 -->
      <VueOfficeDocx
        v-else-if="previewType === 'word'"
        :src="fileInfo?.url"
        class="preview-office"
        @rendered="onRendered"
        @error="onError"
      />

      <!-- Excel 预览 -->
      <div v-else-if="previewType === 'excel'" class="preview-excel-wrapper">
        <VueOfficeExcel
          :src="fileInfo?.url"
          :options="excelConfig"
          class="preview-excel"
          @rendered="onRendered"
          @error="onError"
        />
      </div>

      <!-- 视频预览 -->
      <div v-else-if="previewType === 'video'" class="preview-container">
        <video :src="fileInfo?.url" controls class="preview-video">
          您的浏览器不支持视频播放
        </video>
      </div>

      <!-- 音频预览 -->
      <div v-else-if="previewType === 'audio'" class="preview-container">
        <audio :src="fileInfo?.url" controls class="preview-audio">
          您的浏览器不支持音频播放
        </audio>
      </div>

      <!-- 不支持的格式 -->
      <div v-else class="preview-unsupported">
        <a-result status="warning" title="暂不支持该格式预览">
          <template #extra>
            <span class="text-gray-500">
              文件类型: {{ fileInfo?.ext || '未知' }}
            </span>
          </template>
        </a-result>
      </div>
    </Spin>
  </a-modal>
</template>

<style scoped>
.preview-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.preview-image {
  max-width: 100%;
  max-height: 75vh;
  object-fit: contain;
}

.preview-office {
  width: 100%;
  height: 75vh;
}

.preview-excel-wrapper {
  width: 100%;
  height: 75vh;
  overflow: auto;
}

.preview-excel {
  width: 100%;
  min-height: 100%;
}

.preview-video {
  max-width: 100%;
  max-height: 75vh;
}

.preview-audio {
  width: 100%;
}

.preview-unsupported {
  padding: 40px 0;
}
</style>
