<script setup lang="ts">
import { computed } from 'vue';

import { VbenIcon } from '@vben/icons';

defineOptions({ name: 'FileItem' });

const props = defineProps({
  data: {
    type: Object,
    required: true,
  },
  size: {
    type: Number,
    default: 48,
  },
});

/** 判断是否为图片类型 */
const isImage = computed(
  () => props.data.category === 'IMAGE' && props.data.url,
);

/** 文件类型与图标的映射 */
const iconConfig: Record<string, { color: string; icon: string }> = {
  '': { color: '#faad14', icon: 'carbon:folder' },
  AUDIO: { color: '#eb2f96', icon: 'carbon:music' },
  DOCUMENT: { color: '#1677ff', icon: 'carbon:document' },
  IMAGE: { color: '#52c41a', icon: 'carbon:image' },
  OTHER: { color: '#8c8c8c', icon: 'carbon:help' },
  VIDEO: { color: '#722ed1', icon: 'carbon:video' },
};

/** 获取图标配置 */
const currentIcon = computed(() => {
  const category = props.data.category || '';
  return iconConfig[category] ?? iconConfig.OTHER!;
});
</script>

<template>
  <div
    class="file-preview"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <!-- 图片类型显示缩略图 -->
    <a-image
      v-if="isImage"
      :src="props.data.url"
      :preview-mask="false"
      class="file-image"
    />
    <!-- 其他类型显示图标 -->
    <VbenIcon
      v-else
      :icon="currentIcon.icon"
      :style="{ color: currentIcon.color, fontSize: `${size * 0.6}px` }"
    />
  </div>
</template>

<style scoped>
.file-preview {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 6px;
}

.file-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}
</style>
