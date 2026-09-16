<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { EnvironmentOutlined, SearchOutlined } from '@ant-design/icons-vue';
import { Card, Empty, Input, notification, Spin } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getAreaTree } from '#/api';
import { defHttp } from '#/api/request';
import { $t } from '#/locales';

/** 搜索关键词 */
const searchValue = ref('');
/** 加载状态 */
const loading = ref(false);
/** 展开的节点 */
const expandedKeys = ref<string[]>([]);

const [BaseForm, baseFormApi] = useVbenForm({
  // 所有表单项共用，可单独在表单内覆盖
  commonConfig: {
    // 所有表单项
    componentProps: {
      class: 'w-full',
    },
  },
  // 提交函数
  handleSubmit: onSubmit,
  layout: 'horizontal',
  schema: [
    {
      fieldName: 'parentId',
      component: 'Input',
      label: '上级国标码',
      defaultValue: 0,
      componentProps: {
        disabled: true,
        placeholder: '请输入上级国标码',
      },
      rules: 'required',
    },
    {
      fieldName: 'id',
      component: 'Input',
      label: '国标码',
      componentProps: {
        placeholder: '请输入国标码',
      },
      rules: 'required',
    },
    {
      fieldName: 'name',
      component: 'Input',
      label: '地址名称',
      componentProps: {
        placeholder: '请输入地址名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'level',
      component: 'RadioGroup',
      label: '级别',
      defaultValue: 1,
      componentProps: {
        options: [
          { label: '省份', value: 1 },
          { label: '城市', value: 2 },
          { label: '区县', value: 3 },
          { label: '乡镇', value: 4 },
        ],
      },
    },
    {
      fieldName: 'longitude',
      component: 'Input',
      label: '经度',
      componentProps: {
        placeholder: '请填经度',
      },
      help: '请填写正确的经纬度,以便地图定位',
    },
    {
      fieldName: 'latitude',
      component: 'Input',
      label: '纬度',
      componentProps: {
        placeholder: '请填经度',
      },
      help: '请填写正确的经纬度,以便地图定位',
    },
    {
      fieldName: 'sequence',
      component: 'InputNumber',
      label: '排序',
      defaultValue: 0,
      componentProps: {
        placeholder: '请填写排序',
        min: 0,
        max: 100,
      },
      help: '数值越小优先级越高',
    },
    {
      fieldName: 'source',
      component: 'Textarea',
      label: '来源',
      componentProps: {
        placeholder: '请输入数据来源',
        rows: 3,
      },
      rules: 'required',
    },
  ],
});

function onSubmit(values: Record<string, any>) {
  defHttp.post('/iam/areas', values).then(() => {
    baseFormApi.resetForm();
    loadAreaTree();
    notification.success({
      description: '提交成功',
      duration: 3,
      message: $t('authentication.loginSuccess'),
    });
  });
}

onMounted(async () => {
  loadAreaTree();
});

function handleSelect(_checkedKeys: any, event: any) {
  if (!event.selected) {
    return;
  }
  event.selectedNodes[0].name = event.selectedNodes[0].label;
  baseFormApi.setValues({
    ...event.selectedNodes[0],
  });
}

const treeData = ref<any[]>([]);

/** 加载地区树 */
function loadAreaTree() {
  loading.value = true;
  getAreaTree()
    .then((ret: any) => {
      treeData.value = ret;
    })
    .finally(() => {
      loading.value = false;
    });
}

/** 搜索地区 - 前端过滤 */
function filterTree(nodes: any[], keyword: string): any[] {
  if (!keyword) return nodes;
  const result: any[] = [];
  for (const node of nodes) {
    const isMatch = node.name?.toLowerCase().includes(keyword.toLowerCase());
    const filteredChildren = node.children?.length
      ? filterTree(node.children, keyword)
      : [];
    if (isMatch || filteredChildren.length > 0) {
      result.push({
        ...node,
        children:
          filteredChildren.length > 0 ? filteredChildren : node.children,
      });
    }
  }
  return result;
}

/** 获取需要展开的节点keys */
function getExpandKeys(nodes: any[], keyword: string): string[] {
  const keys: string[] = [];
  function traverse(items: any[], parentKeys: string[]) {
    for (const node of items) {
      const isMatch = node.name?.toLowerCase().includes(keyword.toLowerCase());
      if (isMatch) {
        keys.push(...parentKeys);
      }
      if (node.children?.length) {
        traverse(node.children, [...parentKeys, node.value]);
      }
    }
  }
  traverse(nodes, []);
  return [...new Set(keys)];
}

/** 搜索时展开匹配节点 */
function onSearch() {
  if (!searchValue.value) {
    expandedKeys.value = [];
    return;
  }
  expandedKeys.value = getExpandKeys(treeData.value, searchValue.value);
}

/** 过滤后的树数据 */
const filteredTreeData = computed(() => {
  if (!searchValue.value) return treeData.value;
  return filterTree(treeData.value, searchValue.value);
});
</script>

<template>
  <div class="area-container">
    <Card class="area-tree-card">
      <template #title>
        <div class="card-title">
          <EnvironmentOutlined />
          <span>地区树</span>
        </div>
      </template>
      <template #extra>
        <a-tooltip placement="left">
          <template #title>
            鉴于地址变动频率较低且不易维护，故禁用编辑。有需求请 Fork
            代码放开限制。
          </template>
          <a-tag color="warning">只读模式</a-tag>
        </a-tooltip>
      </template>

      <!-- 搜索框 -->
      <div class="search-wrapper">
        <Input
          v-model:value="searchValue"
          placeholder="输入地区名称搜索..."
          allow-clear
          @change="onSearch"
        >
          <template #prefix>
            <SearchOutlined style="color: #bfbfbf" />
          </template>
        </Input>
      </div>

      <!-- 树形结构 -->
      <Spin :spinning="loading">
        <div v-if="filteredTreeData.length > 0" class="tree-wrapper">
          <a-tree
            v-model:expanded-keys="expandedKeys"
            :field-names="{
              children: 'children',
              title: 'name',
              key: 'value',
            }"
            :tree-data="filteredTreeData"
            block-node
            @select="handleSelect"
          >
            <template #title="{ name }">
              <span class="tree-title">
                <template
                  v-if="
                    searchValue &&
                    name?.toLowerCase().includes(searchValue.toLowerCase())
                  "
                >
                  <template
                    v-for="(fragment, i) in name.split(
                      new RegExp(`(${searchValue})`, 'gi'),
                    )"
                    :key="i"
                  >
                    <span
                      v-if="
                        fragment.toLowerCase() === searchValue.toLowerCase()
                      "
                      class="highlight"
                    >
                      {{ fragment }}
                    </span>
                    <template v-else>
                      {{ fragment }}
                    </template>
                  </template>
                </template>
                <template v-else>
                  {{ name }}
                </template>
              </span>
            </template>
          </a-tree>
        </div>
        <Empty v-else description="暂无数据" />
      </Spin>
    </Card>

    <Card class="area-form-card" title="地址详情">
      <BaseForm />
    </Card>
  </div>
</template>

<style scoped>
.area-container {
  display: flex;
  flex-direction: row;
  gap: 16px;
  width: 100%;
  padding: 8px;
}

.area-tree-card {
  flex-shrink: 0;
  width: 380px;
  height: calc(100vh - 140px);
  overflow: hidden;
}

.area-tree-card :deep(.ant-card-body) {
  padding: 16px;
}

.area-form-card {
  flex: 1;
  min-width: 0;
}

.card-title {
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-wrapper {
  margin-bottom: 16px;
}

.tree-wrapper {
  height: calc(100vh - 280px);
  padding: 8px 0;
  overflow-y: auto;
}

.tree-wrapper::-webkit-scrollbar {
  width: 6px;
}

.tree-wrapper::-webkit-scrollbar-thumb {
  background-color: #d9d9d9;
  border-radius: 3px;
}

.tree-wrapper::-webkit-scrollbar-thumb:hover {
  background-color: #bfbfbf;
}

.tree-wrapper::-webkit-scrollbar-track {
  background-color: transparent;
}

/* 树节点标题 */
.tree-title {
  padding: 4px 8px;
  font-size: 14px;
  color: #333;
  border-radius: 4px;
  transition: all 0.2s;
}

/* 搜索高亮 */
.highlight {
  font-weight: 500;
  color: #1890ff;
}

/* 树组件样式 */
:deep(.ant-tree) {
  background: transparent;
}

:deep(.ant-tree-treenode) {
  padding: 4px 0;
  transition: all 0.15s ease;
}

:deep(.ant-tree-treenode:hover) {
  background-color: #f5f5f5;
  border-radius: 6px;
}

:deep(.ant-tree-node-content-wrapper) {
  padding: 0;
  line-height: 28px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

:deep(.ant-tree-node-content-wrapper:hover) {
  background-color: transparent;
}

:deep(.ant-tree-node-selected) {
  background-color: #e6f4ff !important;
  border-radius: 4px;
}

:deep(.ant-tree-node-selected .tree-title) {
  font-weight: 500;
  color: #1890ff;
}

/* 展开收起箭头 */
:deep(.ant-tree-switcher) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  color: #999;
  transition: all 0.2s;
}

:deep(.ant-tree-switcher:hover) {
  color: #1890ff;
}

:deep(.ant-tree-switcher-icon) {
  font-size: 10px;
  transition: transform 0.2s ease;
}

/* 缩进线 */
:deep(.ant-tree-indent-unit) {
  width: 20px;
}
</style>
