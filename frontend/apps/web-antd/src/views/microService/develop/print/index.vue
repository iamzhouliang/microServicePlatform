<script setup lang="ts">
import { Page } from '@vben/common-ui';

import PrintDesigner from './PrintDesigner/Designer.vue';
</script>

<template>
  <Page content-class="print-page-content" :auto-content-height="true">
    <PrintDesigner />
  </Page>
</template>

<style scoped lang="less">
:deep(.print-page-content) {
  height: calc(100vh - 140px);
  padding: 12px !important;
  overflow: hidden;
  background: #f5f5f5;
}
</style>
