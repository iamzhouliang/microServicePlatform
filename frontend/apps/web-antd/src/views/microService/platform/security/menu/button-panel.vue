<script setup lang="ts">
/**
 * 按钮权限面板组件
 */
import { computed, ref } from 'vue';

import { ReloadOutlined } from '@ant-design/icons-vue';
import { Button, Card, Tooltip } from 'ant-design-vue';

import ResourceButtonTable from './button.vue';

/** Props */
const props = defineProps<{
  /** 选中的菜单数据 */
  menu: any;
}>();

/** 按钮权限表格引用 */
const tableRef = ref();

/** 是否有选中的菜单 */
const hasSelected = computed(() => !!props.menu);

/** 是否显示按钮权限表格（只有菜单类型才显示） */
const showButtonTable = computed(() => {
  if (!props.menu) return false;
  return props.menu.type === 'menu';
});

/** 暴露方法供父组件调用 */
defineExpose({
  /** 刷新表格 */
  refresh: () => tableRef.value?.doRefresh(),
  /** 设置父ID并刷新 */
  setParentIdAndRefresh: (parentId: string) => {
    if (tableRef.value && showButtonTable.value) {
      const { crudBinding } = tableRef.value;
      if (crudBinding) {
        crudBinding.addForm.initialForm = { parentId };
        crudBinding.editForm.initialForm = { parentId };
        crudBinding.actionbar.buttons.add.show = true;
      }
      tableRef.value.setSearchFormData({ form: { parentId } });
      tableRef.value.doRefresh();
    }
  },
});
</script>

<template>
  <Card class="button-table-card" :bordered="false">
    <template #title>
      <span class="font-medium">按钮权限列表</span>
    </template>
    <template #extra>
      <Tooltip title="刷新">
        <Button
          v-if="showButtonTable"
          type="text"
          size="small"
          @click="tableRef?.doRefresh()"
        >
          <template #icon><ReloadOutlined /></template>
        </Button>
      </Tooltip>
    </template>

    <template v-if="showButtonTable">
      <ResourceButtonTable ref="tableRef" />
    </template>
    <template v-else>
      <div class="flex h-[200px] items-center justify-center text-gray-400">
        {{ hasSelected ? '当前菜单无按钮权限' : '请先选择菜单' }}
      </div>
    </template>
  </Card>
</template>

<style lang="less" scoped>
.button-table-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;

  :deep(.ant-card-body) {
    display: flex;
    flex: 1;
    flex-direction: column;
    padding: 16px;
    overflow: hidden;
  }

  :deep(.fs-crud-container) {
    display: flex;
    flex: 1;
    flex-direction: column;
  }

  :deep(.fs-table-wrapper) {
    flex: 1;
    overflow: auto;
  }
}
</style>
