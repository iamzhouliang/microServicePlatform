<script lang="ts" setup name="SysDictPage">
import { computed, onMounted } from 'vue';

import { Page } from '@vben/common-ui';

import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SyncOutlined,
} from '@ant-design/icons-vue';
import { FsFormWrapper } from '@fast-crud/fast-crud';
import { Button, Card, Empty, InputSearch, Skeleton } from 'ant-design-vue';

import { useDictPage } from './useDict';

const {
  // state
  searchText,
  selectedKeys,
  filteredTree,
  loading,
  // crud
  crudBinding,
  crudRef,
  // form wrapper
  formWrapperRef,
  openFormWrapper,
  formWrapperOptions,
  // actions
  loadDictList,
  syncDict,
  handleMenuClick,
  handleEdit,
  handleDelete,
} = useDictPage();

/** 是否有字典数据 */
const hasDictData = computed(() => filteredTree.value.length > 0);

/** 字典总数 */
const totalCount = computed(() => filteredTree.value.length);

/** 高亮搜索文本 */
function highlightText(
  text: string,
  keyword: string,
): null | { after: string; before: string; match: string } {
  if (!keyword || !text.includes(keyword)) return null;
  const index = text.indexOf(keyword);
  return {
    before: text.slice(0, index),
    match: keyword,
    after: text.slice(index + keyword.length),
  };
}

onMounted(loadDictList);
</script>

<template>
  <Page :auto-content-height="true" content-class="dict-page">
    <div class="dict-page__layout">
      <!-- 左侧字典列表 -->
      <Card :bordered="false" class="dict-page__sidebar">
        <template #title>
          <span class="dict-page__title">字典分类</span>
        </template>
        <template #extra>
          <div class="dict-page__header-actions">
            <Button
              v-access:code="'dict:add'"
              type="primary"
              size="small"
              @click="openFormWrapper('')"
            >
              <template #icon><PlusOutlined /></template>
              新增
            </Button>
            <Button size="small" @click="syncDict">
              <template #icon><SyncOutlined /></template>
              同步
            </Button>
          </div>
          <FsFormWrapper ref="formWrapperRef" v-bind="formWrapperOptions" />
        </template>

        <!-- 搜索框 -->
        <div class="dict-page__search">
          <InputSearch
            v-model:value="searchText"
            placeholder="搜索字典名称或编码"
            allow-clear
          />
        </div>

        <!-- 字典列表容器 -->
        <div class="dict-page__list-wrapper">
          <Skeleton :loading="loading" :paragraph="{ rows: 8 }" active>
            <div class="dict-page__list">
              <template v-if="hasDictData">
                <div
                  v-for="node in filteredTree"
                  :key="node.id"
                  class="dict-page__item"
                  :class="{
                    'dict-page__item--active': selectedKeys.includes(node.id),
                  }"
                  @click="handleMenuClick(node)"
                >
                  <div class="dict-page__item-content">
                    <!-- 字典名称（支持高亮） -->
                    <span class="dict-page__item-name">
                      <template v-if="highlightText(node.name, searchText)">
                        {{ highlightText(node.name, searchText)?.before }}
                        <span class="dict-page__highlight">
                          {{ highlightText(node.name, searchText)?.match }}
                        </span>
                        {{ highlightText(node.name, searchText)?.after }}
                      </template>
                      <template v-else>{{ node.name }}</template>
                    </span>
                    <!-- 字典编码 -->
                    <span class="dict-page__item-code">{{ node.code }}</span>
                  </div>

                  <!-- 操作按钮 -->
                  <div class="dict-page__item-actions">
                    <EditOutlined
                      class="dict-page__action dict-page__action--edit"
                      @click.stop="handleEdit(node)"
                    />
                    <DeleteOutlined
                      class="dict-page__action dict-page__action--delete"
                      @click.stop="handleDelete(node)"
                    />
                  </div>
                </div>
              </template>

              <!-- 空状态 -->
              <div v-else class="dict-page__empty">
                <Empty
                  :image="Empty.PRESENTED_IMAGE_SIMPLE"
                  description="暂无字典数据"
                />
              </div>
            </div>
          </Skeleton>
        </div>

        <!-- 底部统计 -->
        <div class="dict-page__footer">
          共 <span class="dict-page__count">{{ totalCount }}</span> 条记录
        </div>
      </Card>

      <!-- 右侧字典子项 -->
      <Card :bordered="false" class="dict-page__content" title="字典子项">
        <div class="dict-page__crud">
          <fs-crud ref="crudRef" v-bind="crudBinding">
            <template #cell_description="scope">
              <a-tooltip :title="scope.row.description" placement="topLeft">
                {{ scope.row.description }}
              </a-tooltip>
            </template>
            <template #cell_label="scope">
              <a-tooltip :title="scope.row.label" placement="topLeft">
                {{ scope.row.label }}
              </a-tooltip>
            </template>
          </fs-crud>
        </div>
      </Card>
    </div>
  </Page>
</template>

<style lang="less" scoped>
.dict-page {
  &__layout {
    display: flex;
    gap: 12px;
    height: 100%;
    min-height: 0;
    overflow: hidden;
  }

  &__sidebar {
    display: flex;
    flex-shrink: 0;
    flex-direction: column;
    width: 360px;
    min-width: 300px;
    min-height: 0;
    overflow: hidden;

    :deep(.ant-card-head) {
      flex-shrink: 0;
      min-height: auto;
      padding: 12px 16px;
      border-bottom: 1px solid var(--border);
    }

    :deep(.ant-card-body) {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-height: 0;
      padding: 12px;
      overflow: hidden;
    }
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
  }

  &__header-actions {
    display: flex;
    gap: 8px;
  }

  &__search {
    flex-shrink: 0;
    margin-bottom: 12px;
  }

  &__list-wrapper {
    position: relative;
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  &__list {
    height: 100%;
    padding-right: 8px;
    overflow-y: auto;
    overscroll-behavior: contain;
    scroll-behavior: smooth;
    scrollbar-width: none; /* Firefox */

    &::-webkit-scrollbar {
      display: none; /* Chrome, Safari */
    }
  }

  &__footer {
    flex-shrink: 0;
    padding: 12px 0;
    margin-top: 8px;
    font-size: 13px;
    color: #999;
    text-align: center;
    border-top: 1px solid #e5e7eb;
  }

  &__count {
    padding: 2px 8px;
    font-weight: 600;
    color: var(--primary);
    background: var(--primary-100, rgb(var(--primary-rgb) / 10%));
    border-radius: 10px;
  }

  &__item {
    display: flex;
    gap: 8px;
    align-items: center;
    justify-content: space-between;
    padding: 12px;
    margin-bottom: 8px;
    cursor: pointer;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    transition: all 0.2s ease;

    &:hover {
      border-color: #1890ff;
      box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
    }

    &--active {
      background: #fff;
      border: 1px solid #1890ff;
      border-left: 3px solid #1890ff;
      box-shadow: 0 2px 12px rgb(24 144 255 / 15%);

      .dict-page__item-name {
        font-weight: 600;
        color: #1890ff;
      }
    }
  }

  &__item-content {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  &__item-name {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 14px;
    line-height: 1.4;
    white-space: nowrap;
  }

  &__item-code {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 12px;
    line-height: 1.3;
    color: var(--text-muted, #999);
    white-space: nowrap;
  }

  &__highlight {
    padding: 0 2px;
    font-weight: 600;
    color: var(--primary);
    background: var(--primary-100, rgb(var(--primary-rgb) / 10%));
    border-radius: 2px;
  }

  &__item-actions {
    display: flex;
    visibility: hidden;
    gap: 8px;
    opacity: 0;
    transition: all 0.2s ease;
  }

  &__item:hover &__item-actions {
    visibility: visible;
    opacity: 1;
  }

  &__action {
    padding: 6px;
    font-size: 14px;
    cursor: pointer;
    border-radius: 4px;
    transition: all 0.2s ease;

    &--edit:hover {
      color: var(--primary);
      background: var(--primary-100, rgb(var(--primary-rgb) / 10%));
    }

    &--delete:hover {
      color: #ff4d4f;
      background: rgb(255 77 79 / 10%);
    }
  }

  &__empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
  }

  &__content {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    min-height: 0;
    overflow: hidden;

    :deep(.ant-card-head) {
      flex-shrink: 0;
      min-height: auto;
      padding: 12px 16px;
      border-bottom: 1px solid var(--border);
    }

    :deep(.ant-card-body) {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-height: 0;
      padding: 12px;
      overflow: hidden;
    }
  }

  &__crud {
    flex: 1;
    height: 100%;

    :deep(.fs-crud-container) {
      height: 100%;
    }
  }
}
</style>
