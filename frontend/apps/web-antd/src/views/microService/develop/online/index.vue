<script lang="ts" setup name="OnlinePage">
import { onMounted, ref } from 'vue';

import { useFs } from '@fast-crud/fast-crud';

import createCrudOptions from './crud';
import FormDesignModal from './form-design.vue';

/** 表单设计弹窗状态 */
const designModalOpen = ref(false);
const currentModelId = ref<string>();
const currentModelTitle = ref<string>();

/** 打开表单设计弹窗 */
function openDesignModal(row: {
  definitionKey: string;
  id: string;
  title: string;
}) {
  currentModelId.value = row.id;
  currentModelTitle.value = `${row.title}（${row.definitionKey}）`;
  designModalOpen.value = true;
}

/** 设计成功后刷新列表 */
function onDesignSuccess() {
  crudExpose.doRefresh();
}

// 通过context传递openDesignModal到crud.tsx中
const { crudBinding, crudRef, crudExpose } = useFs({
  createCrudOptions,
  context: { openDesignModal },
});

// 页面打开后获取列表数据
onMounted(async () => {
  await crudExpose.doRefresh();
});
</script>

<template>
  <fs-page>
    <fs-crud ref="crudRef" v-bind="crudBinding">
      <template #cell_description="scope">
        <a-tooltip :title="scope.row.description" placement="top">
          {{ scope.row.description }}
        </a-tooltip>
      </template>
    </fs-crud>

    <!-- 表单设计弹窗 -->
    <FormDesignModal
      v-model:open="designModalOpen"
      :model-id="currentModelId"
      :model-title="currentModelTitle"
      @success="onDesignSuccess"
    />
  </fs-page>
</template>
