<script lang="ts" setup>
/**
 * 网关限流管理
 */
import { onMounted } from 'vue';

import { useFs } from '@fast-crud/fast-crud';

import createCrudOptions from './crud';

defineOptions({ name: 'GatewayLimitPageList' });

const { crudRef, crudBinding, crudExpose } = useFs({ createCrudOptions });

onMounted(() => crudExpose.doRefresh());
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding" />
  </fs-page>
</template>
