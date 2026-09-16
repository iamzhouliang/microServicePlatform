<script setup lang="ts">
/**
 * 通用数据选择器弹窗
 * 严格参考 assign-user.vue 使用 Transfer + Table 实现
 */
import type { DataItem, DataSelectorConfig } from './types';

import { computed, ref, watch } from 'vue';

import { difference } from 'lodash-es';

interface Props {
  open: boolean;
  config: DataSelectorConfig;
  value?: (number | string)[];
  dataSource?: DataItem[];
}

const props = withDefaults(defineProps<Props>(), {
  value: () => [],
  dataSource: () => [],
});

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'update:value', value: (number | string)[]): void;
  (e: 'confirm', value: (number | string)[], items: DataItem[]): void;
  (e: 'cancel'): void;
}>();

// 表格列配置
const tableColumns = [{ dataIndex: 'name', title: '名称' }];

// 内部状态
const loading = ref(false);
const transferData = ref<any[]>([]);
const targetKeys = ref<string[]>([]);

// 控制弹窗显示
const modalVisible = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val),
});

// 加载数据
async function loadData() {
  let data: DataItem[] = [];
  if (props.config.loadData) {
    loading.value = true;
    try {
      data = await props.config.loadData();
    } finally {
      loading.value = false;
    }
  } else {
    data = props.dataSource || [];
  }
  // 转换为 Transfer 需要的格式
  transferData.value = data.map((item) => ({
    key: String(item.id),
    title: item.name,
    name: item.name,
    disabled: item.disabled,
  }));
}

// Transfer onChange
function handleChange(nextTargetKeys: string[]) {
  targetKeys.value = nextTargetKeys;
}

// 表格行选择配置
function getRowSelection({ selectedKeys, onItemSelectAll, onItemSelect }: any) {
  return {
    onSelectAll(selected: boolean, selectedRows: any[]) {
      const treeSelectedKeys = selectedRows
        .filter((item: any) => !item.disabled)
        .map(({ key }: any) => key);
      const diffKeys = selected
        ? difference(treeSelectedKeys, selectedKeys)
        : difference(selectedKeys, treeSelectedKeys);
      onItemSelectAll(diffKeys, selected);
    },
    onSelect({ key }: any, selected: boolean) {
      onItemSelect(key, selected);
    },
    selectedRowKeys: selectedKeys,
  };
}

// 点击行直接移动（左侧点击添加，右侧点击移除）
function getCustomRow(direction: 'left' | 'right') {
  return (record: any) => ({
    onClick: () => {
      if (record.disabled) return;
      if (direction === 'left') {
        // 左侧点击：添加到右侧
        if (!targetKeys.value.includes(record.key)) {
          targetKeys.value = [...targetKeys.value, record.key];
        }
      } else {
        // 右侧点击：从右侧移除
        targetKeys.value = targetKeys.value.filter((k) => k !== record.key);
      }
    },
    style: { cursor: record.disabled ? 'not-allowed' : 'pointer' },
  });
}

// 搜索过滤
function filterOption(inputValue: string, item: any) {
  return item.name.toLowerCase().includes(inputValue.toLowerCase());
}

// 确认选择
function handleConfirm() {
  const ids = targetKeys.value.map((k) =>
    Number.isNaN(Number(k)) ? k : Number(k),
  );
  const items = transferData.value
    .filter((item) => targetKeys.value.includes(item.key))
    .map((item) => ({ id: item.key, name: item.name }));
  emit('update:value', ids);
  emit('confirm', ids, items as DataItem[]);
  modalVisible.value = false;
}

// 取消
function handleCancel() {
  emit('cancel');
  modalVisible.value = false;
}

// 监听弹窗打开
watch(
  () => props.open,
  (val) => {
    if (val) {
      targetKeys.value = (props.value || []).map(String);
      loadData();
    }
  },
);
</script>

<template>
  <a-modal
    v-model:open="modalVisible"
    :title="config.title"
    width="80vw"
    destroy-on-close
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <a-transfer
      :data-source="transferData"
      :filter-option="filterOption"
      :show-search="config.showSearch !== false"
      :show-select-all="false"
      :target-keys="targetKeys"
      :titles="['可选数据', '已选数据']"
      @change="handleChange"
    >
      <template
        #children="{
          direction,
          filteredItems,
          selectedKeys,
          onItemSelectAll,
          onItemSelect,
        }"
      >
        <a-table
          :columns="tableColumns"
          :custom-row="getCustomRow(direction)"
          :data-source="filteredItems"
          :pagination="{ pageSize: 10 }"
          :row-selection="
            getRowSelection({
              selectedKeys,
              onItemSelectAll,
              onItemSelect,
            })
          "
          size="small"
        />
      </template>
    </a-transfer>
  </a-modal>
</template>

<style scoped>
:deep(.ant-transfer) {
  display: flex;
  justify-content: space-between;
}

:deep(.ant-transfer-list) {
  flex: 1;
}
</style>
