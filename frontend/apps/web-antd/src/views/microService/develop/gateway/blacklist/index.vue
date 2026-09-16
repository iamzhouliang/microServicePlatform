<script lang="ts" setup>
/**
 * 网关黑名单管理
 * 数据存储在 Redis 中，前端做本地分页
 */
import { onMounted, ref } from 'vue';

import { useFs } from '@fast-crud/fast-crud';

import { GetList } from './api';
import createCrudOptions from './crud';

defineOptions({ name: 'GatewayBlackPageList' });

const localDataRef = ref();
const { crudRef, crudBinding, crudExpose } = useFs({
  createCrudOptions,
  context: { localDataRef },
});

onMounted(async () => {
  // 加载全部数据用于本地分页
  localDataRef.value = await GetList({
    page: { offset: 0, current: 1, size: 99_999_999 },
    query: {},
    sort: {},
  });
  await crudExpose.doRefresh();
});
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud v-if="crudBinding" ref="crudRef" v-bind="crudBinding">
      <template #actionbar-right>
        <a-alert
          class="ml-1"
          message="限访名单存储在Redis中，为了查询方便做的本地分页，一般黑名单也不会太多"
          style="margin-top: 10px"
          type="info"
        />
      </template>
    </fs-crud>
  </fs-page>
</template>
