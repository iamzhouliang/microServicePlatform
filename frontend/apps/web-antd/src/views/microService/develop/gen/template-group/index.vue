<script lang="ts" setup>
/**
 * 模板分组管理页面
 * 用于管理代码生成模板的分组
 */
import { onMounted } from 'vue';

import { useFs } from '@fast-crud/fast-crud';

import createCrudOptions from './crud';

const { crudRef, crudBinding, crudExpose } = useFs({
  createCrudOptions,
});

onMounted(() => {
  crudExpose.doRefresh();
});
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding" />
  </fs-page>
</template>
