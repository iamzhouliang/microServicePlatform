<script lang="ts" setup name="FilePageList">
import { computed, nextTick, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { VbenIcon } from '@vben/icons';

import { UploadOutlined } from '@ant-design/icons-vue';
import { useFs } from '@fast-crud/fast-crud';
import { Card, message } from 'ant-design-vue';

import { defHttp } from '#/api/request';

import * as api from './api';
import createCrudOptions from './crud';
import FileItem from './FileItem.vue';
import FilePreview from './FilePreview.vue';

/** 文件分类数据 */
const categoryData = ref([
  { category: '', icon: 'carbon:folder', name: '全部' },
  { category: 'IMAGE', icon: 'carbon:image', name: '图片' },
  { category: 'VIDEO', icon: 'carbon:video', name: '视频' },
  { category: 'AUDIO', icon: 'carbon:music', name: '音频' },
  { category: 'DOCUMENT', icon: 'carbon:document', name: '文档' },
  { category: 'OTHER', icon: 'carbon:help', name: '其它' },
]);
/** 当前选中的分类 */
const selectedCategory = ref('');
const selectedKeys = ref<string[]>(['']);
const showTableRef = ref(false);

/** 文件预览 */
const previewOpen = ref(false);
const previewFile = ref<any>({});

/** 是否显示表格模式 */
const showTableComputed = computed(() => showTableRef.value);

const { crudBinding, crudRef, crudExpose } = useFs({
  createCrudOptions,
  context: {
    showTableComputed,
    showTableRef,
    selectedCategory,
    permission: 'sys:user',
    onPreview: (item: any) => handlePreview(item),
  },
});

onMounted(() => {
  nextTick(() => {
    crudExpose.doRefresh();
  });
});

/** 点击分类 */
function handleCategoryClick(item: { category: string }) {
  selectedCategory.value = item.category;
  selectedKeys.value = [item.category];
  crudExpose.doRefresh();
}

/** 上传文件 */
function handleUpload(options: any) {
  const controller = new AbortController();
  (async function requestWrap() {
    const { onProgress, onError, onSuccess, file, name = 'file' } = options;
    const formData = new FormData();
    formData.append(name as string, file as File);
    try {
      const res = await defHttp.post('/suite/oss/files/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        onUploadProgress: (progressEvent: any) => {
          const complete = (progressEvent.loaded / progressEvent.total) * 100;
          onProgress({ percent: Math.round(complete) });
        },
      });
      message.success('上传成功');
      onSuccess(res);
      crudExpose.doRefresh();
    } catch (error) {
      onError(error);
    }
  })();
  return {
    abort() {
      controller.abort();
    },
  };
}
/** 删除文件 */
function doRemove(opts: any) {
  crudExpose.doRemove(opts);
}

/** 下载文件 */
function doDownload(item: any) {
  message.info({ content: '开始下载', duration: 3 });
  api.downloadFile(item.url, item.originalFilename);
}

/** 预览文件 */
function handlePreview(item: any) {
  previewFile.value = item;
  previewOpen.value = true;
}
</script>

<template>
  <Page content-class="file-page-content">
    <!-- 左侧分类面板 -->
    <Card :bordered="false" class="category-card">
      <template #title>
        <span class="font-medium">文件分类</span>
      </template>
      <div class="category-list">
        <div
          v-for="item in categoryData"
          :key="item.category"
          class="category-item"
          :class="{ active: selectedKeys.includes(item.category) }"
          @click="handleCategoryClick(item)"
        >
          <VbenIcon :icon="item.icon" class="category-icon" />
          <span class="category-name">{{ item.name }}</span>
        </div>
      </div>
    </Card>
    <!-- 右侧文件列表 -->
    <Card class="file-list-card" title="文件管理">
      <fs-crud ref="crudRef" v-bind="crudBinding">
        <template #actionbar-left>
          <a-upload :custom-request="handleUpload" :show-upload-list="false">
            <a-button type="primary">
              <UploadOutlined />
              文件上传
            </a-button>
          </a-upload>

          <a-tooltip title="切换布局：表格/卡片">
            <span class="ml-3 flex items-center gap-1">
              <span class="text-gray-500">卡片</span>
              <a-switch v-model:checked="showTableRef" />
              <span class="text-gray-500">表格</span>
            </span>
          </a-tooltip>
        </template>
        <template #cell_originalFilename="scope">
          <a-tooltip :title="scope.row.originalFilename" placement="topLeft">
            {{ scope.row.originalFilename }}
          </a-tooltip>
        </template>
        <!-- 表格模式: 预览列 -->
        <template #cell_url="scope">
          <FileItem :data="scope.row" />
        </template>

        <!-- 卡片模式: 替换表格区域 -->
        <template v-if="!showTableComputed" #default>
          <div class="card-grid">
            <a-empty v-if="!crudBinding.data?.length" description="暂无文件" />
            <div
              v-for="(item, index) of crudBinding.data"
              :key="item.id"
              class="file-card"
            >
              <div class="file-card-content">
                <FileItem :data="item" :size="64" />
                <span class="file-name" :title="item.originalFilename">
                  {{ item.originalFilename }}
                </span>
                <span
                  class="file-path"
                  :title="item.storagePath || item.objectPath || item.path"
                >
                  {{ item.storagePath || item.objectPath || item.path || '-' }}
                </span>
              </div>
              <div class="file-card-actions">
                <a-tooltip title="预览">
                  <VbenIcon
                    icon="carbon:view"
                    class="action-icon"
                    @click="handlePreview(item)"
                  />
                </a-tooltip>
                <a-tooltip title="下载">
                  <VbenIcon
                    icon="carbon:download"
                    class="action-icon"
                    @click="doDownload(item)"
                  />
                </a-tooltip>
                <a-tooltip title="删除">
                  <VbenIcon
                    icon="carbon:trash-can"
                    class="action-icon text-red-500"
                    @click="doRemove({ index, row: item })"
                  />
                </a-tooltip>
              </div>
            </div>
          </div>
        </template>
      </fs-crud>
    </Card>

    <!-- 文件预览弹窗 -->
    <FilePreview v-model:open="previewOpen" :file-info="previewFile" />
  </Page>
</template>

<style lang="less" scoped>
/* 页面整体布局 */
:deep(.file-page-content) {
  display: flex;
  gap: 12px;
  height: calc(100vh - 120px);
  overflow: hidden;
}

/* 左侧分类卡片 */
.category-card {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  width: 200px;
  min-width: 180px;
  height: 100%;
  overflow: hidden;

  :deep(.ant-card-body) {
    flex: 1;
    padding: 8px;
    overflow-y: auto;
  }
}

/* 分类列表 */
.category-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.category-item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    background: #f5f5f5;
  }

  &.active {
    color: #fff;
    background: var(--primary-color, #1677ff);

    .category-icon {
      color: #fff;
    }
  }
}

.category-icon {
  font-size: 20px;
  color: #666;
  transition: color 0.2s;
}

.category-name {
  font-size: 14px;
  font-weight: 500;
}

/* 右侧文件列表卡片 */
.file-list-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  overflow: hidden;

  :deep(.ant-card-body) {
    flex: 1;
    padding: 12px;
    overflow: hidden;
  }

  :deep(.fs-crud-container) {
    height: 100%;
  }
}

/* 卡片网格布局 */
.card-grid {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
  align-content: flex-start;
  padding: 16px;
  overflow-y: auto;
}

/* 文件卡片 */
.file-card {
  display: flex;
  flex-direction: column;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    border-color: #d9d9d9;
    box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
  }
}

.file-card-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  cursor: pointer;
}

.file-name {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: #333;
  text-align: center;
  white-space: nowrap;
}

.file-path {
  width: 100%;
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 11px;
  color: #667085;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-card-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding-top: 8px;
  margin-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.action-icon {
  font-size: 16px;
  color: #666;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: var(--primary-color, #1677ff);
  }
}
</style>
