<script lang="ts" setup name="AssignResource">
/**
 * 角色权限分配组件
 * 使用 fast-crud ui 适配层方式
 */
import { reactive, ref } from 'vue';

import { useUi } from '@fast-crud/ui-interface';
import { Button, notification } from 'ant-design-vue';

import { getResourceTree } from '#/api';
import { CheckableTreeSelect } from '#/components/tree';

import * as api from './api';

const { ui } = useUi();

/** 树数据 */
const treeData = ref<any[]>([]);
/** 选中的节点 key */
const checkedKeys = ref<(number | string)[]>([]);
/** 保存中 */
const saving = ref(false);

const state = reactive({
  roleId: '' as string,
  drawerTitle: '功能权限',
  drawerShow: false,
});

/**
 * 打开抽屉
 */
async function open(row: { roleId: string }) {
  state.roleId = row.roleId;
  state.drawerShow = true;

  // 加载菜单树
  const ret = await getResourceTree();
  treeData.value = ret;

  // 获取当前角色已有权限
  const permissions = await api.getRolePermissions(state.roleId);
  checkedKeys.value = permissions
    ? [...(permissions.menuIdList || []), ...(permissions.buttonIdList || [])]
    : [];
}

/**
 * 保存
 */
async function handleSave() {
  saving.value = true;
  try {
    await api.assignResource({
      roleId: state.roleId,
      resIdList: checkedKeys.value,
    });
    notification.success({
      message: '权限分配成功',
      duration: 3,
    });
    state.drawerShow = false;
  } finally {
    saving.value = false;
  }
}

/**
 * 取消
 */
function handleCancel() {
  state.drawerShow = false;
}

defineExpose({
  open,
});
</script>

<template>
  <component
    :is="ui.drawer.name"
    v-if="state.drawerShow"
    v-model:[ui.drawer.visible]="state.drawerShow"
    :title="state.drawerTitle"
    width="50%"
    class="assign-resource-drawer"
  >
    <CheckableTreeSelect
      v-model:value="checkedKeys"
      :check-strictly="true"
      :field-names="{ key: 'id', title: 'title', children: 'children' }"
      class="h-full"
      max-height="calc(100vh - 200px)"
      :tree-data="treeData"
    />
    <template #footer>
      <div class="flex justify-end gap-2">
        <Button @click="handleCancel">取消</Button>
        <Button :loading="saving" type="primary" @click="handleSave">
          保存
        </Button>
      </div>
    </template>
  </component>
</template>

<style lang="less">
.assign-resource-drawer {
  .checkable-tree-select {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
}
</style>
