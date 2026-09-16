<script lang="ts" setup>
/**
 * 数据源表列表组件
 * 用于选择未配置的数据库表并导入到代码生成配置
 */
import type { Key } from 'ant-design-vue/es/table/interface';

import { ref, watch } from 'vue';

import { Button, Input, message, Modal, Table } from 'ant-design-vue';

import * as api from '../table/api';

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits(['close', 'update:visible', 'success']);

const dataSource = ref([]);
const loading = ref(false);
const selectedRowKeys = ref<Key[]>([]);
const searchQuery = ref('');

/** 表格列定义 */
const columns = [
  { title: '表名', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'comment', key: 'comment' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
];

/** 获取数据源表列表 */
const fetchData = async (tableName = '') => {
  loading.value = true;
  try {
    const res = await api.GetDsList({ tableName });
    dataSource.value = res;
  } catch (error) {
    console.error('获取表列表失败:', error);
  } finally {
    loading.value = false;
  }
};

/** 搜索表 */
const handleSearch = () => {
  fetchData(searchQuery.value);
};

/** 处理行选择变化 */
const handleSelectChange = (selectedKeys: Key[]) => {
  selectedRowKeys.value = selectedKeys;
};

/** 执行导入操作 */
const executeImport = async () => {
  const tables = selectedRowKeys.value;
  if (tables.length === 0) {
    message.warning('请至少选择一个表');
    return;
  }
  await api.ImportDs(tables);
  message.success('导入成功');
  emit('success');
  handleClose();
};

/** 关闭弹窗 */
const handleClose = () => {
  selectedRowKeys.value = [];
  emit('close');
  emit('update:visible', false);
};

watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      fetchData();
    }
  },
);
</script>

<template>
  <Modal
    :footer="null"
    :open="props.visible"
    title="未配置可导入的表信息"
    width="70%"
    @cancel="handleClose"
  >
    <div style="margin-bottom: 16px">
      <Input.Search
        v-model:value="searchQuery"
        enter-button
        placeholder="表名..."
        @search="handleSearch"
      />
    </div>
    <Table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :row-selection="{
        selectedRowKeys,
        onChange: handleSelectChange,
      }"
      :scroll="{ y: 300 }"
      row-key="name"
    />
    <div style="margin-top: 16px; text-align: right">
      <Button type="primary" @click="executeImport">导入</Button>
    </div>
  </Modal>
</template>
