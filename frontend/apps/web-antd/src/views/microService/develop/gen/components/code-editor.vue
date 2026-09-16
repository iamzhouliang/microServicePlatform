<script lang="ts" setup>
/**
 * 代码编辑器组件
 * 基于 CodeMirror 实现的代码编辑器，支持 Java 语法高亮和只读模式
 */
import type { Extension } from '@codemirror/state';

import { computed, ref, watch } from 'vue';
import useClipboard from 'vue-clipboard3';
import { Codemirror } from 'vue-codemirror';

import { java } from '@codemirror/lang-java';
import { EditorState } from '@codemirror/state';
import { oneDark } from '@codemirror/theme-one-dark';
import { message } from 'ant-design-vue';

const props = defineProps<{
  command?: string;
  height?: number | string;
  readOnly?: boolean;
}>();

const emit = defineEmits(['update:command']);
const { toClipboard } = useClipboard();

/** 双向绑定的编辑器内容 */
const innerCommand = computed({
  get: () => props.command || '',
  set: (value) => {
    emit('update:command', value);
  },
});

/** 计算编辑器高度样式 */
const editorHeight = computed(() => {
  if (!props.height) return '360px';
  if (typeof props.height === 'string') return props.height;
  return `${props.height}px`;
});
/** 是否只读 */
const readOnly = ref(props.readOnly || false);

/** CodeMirror 扩展配置 */
const computedExtensions = computed<Extension[]>(() => [
  java(),
  oneDark,
  EditorState.readOnly.of(readOnly.value),
]);

/** 复制代码到剪贴板 */
const handleCopy = async () => {
  try {
    await toClipboard(innerCommand.value);
    message.success('代码已复制到剪贴板');
  } catch (error) {
    message.error('复制失败');
    console.error('复制失败:', error);
  }
};

// 监听 props.readOnly 变化，更新编辑器只读状态
watch(
  () => props.readOnly,
  (newVal) => {
    readOnly.value = newVal ?? false;
  },
);
</script>

<template>
  <div class="code-editor">
    <div class="toolbar">
      <a-button type="primary" @click="handleCopy">复制代码</a-button>
    </div>
    <Codemirror
      v-model="innerCommand"
      :readonly="readOnly"
      :style="{ height: editorHeight, flex: 1 }"
      basic
      :tab-size="2"
      :autofocus="true"
      :extensions="computedExtensions"
    />
  </div>
</template>

<style scoped>
.code-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  font-size: 14px;
  line-height: 150%;
}

.toolbar {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  margin-bottom: 8px;
}
</style>
