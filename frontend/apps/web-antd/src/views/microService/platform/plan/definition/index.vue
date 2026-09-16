<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { useFs } from '@fast-crud/fast-crud';

import { getResourceTree } from '#/api';
import { CheckableTreeSelect } from '#/components/tree';

import createCrudOptions from './crud';

const { crudRef, crudBinding, crudExpose } = useFs({
  createCrudOptions,
  context: { permission: 'plan:definition' },
});

/** 菜单树数据 */
const menuTreeData = ref<any[]>([]);

// 页面打开后获取列表数据和菜单树
onMounted(async () => {
  // 加载菜单树
  const menus = await getResourceTree();
  menuTreeData.value = menus || [];
  // 刷新列表
  await crudExpose.doRefresh();
});
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding">
      <!-- 功能权限选择器 -->
      <template #form_itemIdList="scope">
        <a-form-item-rest>
          <CheckableTreeSelect
            v-model:value="scope.form.itemIdList"
            :check-strictly="true"
            :expand-all-on-init="true"
            :field-names="{ key: 'id', title: 'title', children: 'children' }"
            :max-height="400"
            :tree-data="menuTreeData"
          />
        </a-form-item-rest>
      </template>
    </fs-crud>
  </fs-page>
</template>
