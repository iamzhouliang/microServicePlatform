<script lang="ts" setup>
/**
 * 代码预览组件
 * 用于预览生成的代码文件，支持树形文件导航
 */
import type { Key } from 'ant-design-vue/es/table/interface';

import { ref, watch } from 'vue';

import {
  CodeOutlined,
  DatabaseOutlined,
  FileOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  Html5Outlined,
  SettingOutlined,
} from '@ant-design/icons-vue';
import { Modal, Spin, Tree } from 'ant-design-vue';

import * as api from '../table/api';
import CodeEditor from './code-editor.vue';

const props = defineProps<{
  preId: string;
  visible: boolean;
}>();

const emit = defineEmits(['close', 'update:visible']);

/** 树形数据结构 */
interface TreeNode {
  children: TreeNode[];
  key: string;
  title: string;
}

const treeData = ref<TreeNode[]>([]);
const codeContent = ref('');
const loading = ref(false);
const codeMap = ref<Record<string, string>>({});
/** 展开的节点 keys */
const expandedKeys = ref<string[]>([]);

/** 文件后缀与图标类型的映射 */
const fileIconMap: Record<string, string> = {
  java: 'java',
  xml: 'xml',
  vue: 'vue',
  ts: 'ts',
  tsx: 'ts',
  js: 'js',
  jsx: 'js',
  sql: 'sql',
  html: 'html',
  css: 'css',
  scss: 'css',
  less: 'css',
  json: 'json',
  yml: 'yml',
  yaml: 'yml',
  md: 'md',
  properties: 'config',
};

/**
 * 根据文件名获取图标类型
 */
const getFileIconType = (filename: string): string => {
  const ext = filename.split('.').pop()?.toLowerCase() || '';
  return fileIconMap[ext] || 'file';
};

/**
 * 将文件路径转换为树形结构
 */
const convertToTreeData = (data: Record<string, string>): TreeNode[] => {
  const tree: TreeNode[] = [];
  const map: Record<string, TreeNode> = {};
  const allKeys: string[] = [];

  Object.keys(data).forEach((path) => {
    const parts = path.split('/');
    let currentLevel = tree;

    parts.forEach((part, index) => {
      const fullPath = parts.slice(0, index + 1).join('/');
      if (!map[fullPath]) {
        const node = {
          title: part,
          key: fullPath,
          children: [],
        };
        map[fullPath] = node;
        currentLevel.push(node);
      }
      currentLevel = map[fullPath].children;
    });
  });

  // 收集所有有子节点的 key，用于默认展开
  Object.keys(map).forEach((key) => {
    const node = map[key];
    if (node && node.children.length > 0) {
      allKeys.push(key);
    }
  });
  expandedKeys.value = allKeys;

  const result = tree;
  result.forEach((node) => compressPath(node));
  return result;
};

/**
 * 压缩路径，将只有一个子节点的目录合并显示
 */
const compressPath = (node: TreeNode): void => {
  if (node.children.length === 1 && !codeMap.value[node.key]) {
    const child = node.children[0]!;
    node.title = `${node.title}.${child.title}`;
    node.key = child.key;
    node.children = child.children;
    compressPath(node);
  } else {
    node.children.forEach((child) => compressPath(child));
  }
};

const fetchCodeData = async () => {
  loading.value = true;
  try {
    const res = await api.PreviewCode(props.preId);
    const data = res;
    treeData.value = convertToTreeData(data);
    codeMap.value = data;
    const firstKey = Object.keys(data)[0];
    codeContent.value = firstKey ? data[firstKey] || '' : '';
  } catch (error) {
    console.error('Failed to fetch code data:', error);
  } finally {
    loading.value = false;
  }
};

/**
 * 处理树节点选中事件
 */
const handleSelect = (selectedKeys: Key[]) => {
  if (selectedKeys.length > 0) {
    const selectedKey = String(selectedKeys[0]);
    codeContent.value = codeMap.value[selectedKey] || '';
  }
};

const handleClose = () => {
  emit('close');
  emit('update:visible', false);
};

watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      fetchCodeData();
    }
  },
);
</script>

<template>
  <Modal
    :open="visible"
    title="代码预览"
    :footer="null"
    width="80%"
    @cancel="handleClose"
  >
    <Spin :spinning="loading">
      <div class="preview-container">
        <div class="tree-panel">
          <Tree
            v-model:expanded-keys="expandedKeys"
            :tree-data="treeData"
            show-icon
            @select="handleSelect"
          >
            <template #icon="{ expanded, isLeaf, data }">
              <!-- 文件夹图标 -->
              <template v-if="!isLeaf">
                <FolderOpenOutlined v-if="expanded" style="color: #e8a838" />
                <FolderOutlined v-else style="color: #e8a838" />
              </template>
              <!-- 文件图标 - 根据后缀显示不同颜色和图标 -->
              <template v-else>
                <CodeOutlined
                  v-if="getFileIconType(data.title) === 'java'"
                  style="color: #e76f00"
                />
                <SettingOutlined
                  v-else-if="getFileIconType(data.title) === 'xml'"
                  style="color: #f16529"
                />
                <CodeOutlined
                  v-else-if="getFileIconType(data.title) === 'vue'"
                  style="color: #42b883"
                />
                <CodeOutlined
                  v-else-if="getFileIconType(data.title) === 'ts'"
                  style="color: #3178c6"
                />
                <CodeOutlined
                  v-else-if="getFileIconType(data.title) === 'js'"
                  style="color: #f7df1e"
                />
                <DatabaseOutlined
                  v-else-if="getFileIconType(data.title) === 'sql'"
                  style="color: #336791"
                />
                <Html5Outlined
                  v-else-if="getFileIconType(data.title) === 'html'"
                  style="color: #e34f26"
                />
                <FileTextOutlined
                  v-else-if="getFileIconType(data.title) === 'css'"
                  style="color: #264de4"
                />
                <SettingOutlined
                  v-else-if="
                    ['json', 'yml', 'config'].includes(
                      getFileIconType(data.title),
                    )
                  "
                  style="color: #6d6d6d"
                />
                <FileTextOutlined
                  v-else-if="getFileIconType(data.title) === 'md'"
                  style="color: #083fa1"
                />
                <FileOutlined v-else style="color: #8c8c8c" />
              </template>
            </template>
          </Tree>
        </div>
        <div class="code-panel">
          <CodeEditor
            v-model:command="codeContent"
            :read-only="true"
            height="100%"
          />
        </div>
      </div>
    </Spin>
  </Modal>
</template>

<style scoped>
.ant-spin-nested-loading > div > .ant-spin {
  max-height: 100%;
}

.preview-container {
  display: flex;
  height: 75vh;
  overflow: hidden;
}

.tree-panel {
  width: 30%;
  padding: 8px;
  overflow-y: auto;
  border-right: 1px solid #f0f0f0;
}

.code-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 8px;
  overflow: hidden;
}
</style>
