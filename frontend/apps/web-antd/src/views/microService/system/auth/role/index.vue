<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useFs } from '@fast-crud/fast-crud';

import * as api from './api';
import AssignResource from './assign-resource.vue';
import AssignUser from './assign-user.vue';
import createCrudOptions from './crud';
import DataScopeDrawer from './data-scope-drawer.vue';

const [AssignUserModal, userModalApi] = useVbenModal({
  connectedComponent: AssignUser,
});
const assignResourceRef = ref<InstanceType<typeof AssignResource> | null>(null);
const dataScopeDrawerRef = ref<InstanceType<typeof DataScopeDrawer> | null>(
  null,
);

function assignModal() {
  function userModal(roleId: string) {
    api.getUserByRoleId(roleId).then((data) => {
      userModalApi.setData({ roleId, ...data });
      userModalApi.open();
    });
  }

  function resourceModal(roleId: string) {
    assignResourceRef.value?.open({ roleId });
  }

  // 数据权限
  function dataScopeModal(roleId: string, roleName: string) {
    dataScopeDrawerRef.value?.open({ roleId, roleName });
  }

  return {
    userModal,
    resourceModal,
    dataScopeModal,
  };
}

const assign = assignModal();
const { crudRef, crudBinding, crudExpose } = useFs({
  createCrudOptions,
  context: { assign, permission: 'sys:role' },
});

// 页面打开后获取列表数据
onMounted(async () => {
  await crudExpose.doRefresh();
});
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding">
      <template #cell_description="scope">
        <a-tooltip :title="scope.row.description" placement="top">
          {{ scope.row.description }}
        </a-tooltip>
      </template>
    </fs-crud>
    <AssignUserModal />
    <AssignResource ref="assignResourceRef" />
    <DataScopeDrawer ref="dataScopeDrawerRef" />
  </fs-page>
</template>
