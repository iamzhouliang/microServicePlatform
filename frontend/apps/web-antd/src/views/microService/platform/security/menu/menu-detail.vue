<script setup lang="ts">
/**
 * 菜单详情组件
 */
import { computed } from 'vue';

import { DeleteOutlined, EditOutlined } from '@ant-design/icons-vue';
import {
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Tag,
} from 'ant-design-vue';

/** Props */
const props = defineProps<{
  /** 选中的菜单数据 */
  menu: any;
}>();

/** Emits */
const emit = defineEmits<{
  (e: 'edit'): void;
  (e: 'delete'): void;
}>();

/** 是否有选中的菜单 */
const hasSelected = computed(() => !!props.menu);

/** 菜单类型映射 */
const typeMap: Record<string, { color: string; label: string }> = {
  directory: { label: '目录', color: 'blue' },
  menu: { label: '菜单', color: 'green' },
  link: { label: '外链', color: 'orange' },
  iframe: { label: '内嵌', color: 'purple' },
};
</script>

<template>
  <Card class="menu-detail-card" :bordered="false">
    <template #title>
      <span class="font-medium">菜单详情</span>
    </template>
    <template #extra>
      <div v-if="hasSelected" class="flex items-center gap-2">
        <Button type="primary" size="small" @click="emit('edit')">
          <template #icon><EditOutlined /></template>
          编辑
        </Button>
        <Button danger size="small" @click="emit('delete')">
          <template #icon><DeleteOutlined /></template>
          删除
        </Button>
      </div>
    </template>

    <!-- 详情内容 -->
    <template v-if="hasSelected">
      <Descriptions :column="3" :label-style="{ width: '100px' }">
        <DescriptionsItem label="菜单类型">
          <Tag :color="typeMap[menu.type]?.color">
            {{ typeMap[menu.type]?.label || menu.type }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="菜单状态">
          <Tag :color="menu.status ? 'success' : 'error'">
            {{ menu.status ? '正常' : '停用' }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="显示状态">
          <Tag :color="menu.visible ? 'success' : 'warning'">
            {{ menu.visible ? '显示' : '隐藏' }}
          </Tag>
        </DescriptionsItem>

        <DescriptionsItem label="菜单名称">
          {{ menu.title }}
        </DescriptionsItem>
        <DescriptionsItem label="路由地址">
          {{ menu.path || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="路由参数">
          {{ menu.query || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="权限字符">
          {{ menu.permission || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="排序">
          {{ menu.sequence ?? 0 }}
        </DescriptionsItem>
        <DescriptionsItem label="是否外链">
          <Tag :color="menu.type === 'link' ? 'success' : ''">
            {{ menu.type === 'link' ? '是' : '否' }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="是否缓存">
          <Tag :color="menu.keepAlive ? 'success' : ''">
            {{ menu.keepAlive ? '是' : '否' }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="组件路径">
          {{ menu.component || menu.url || '-' }}
        </DescriptionsItem>
      </Descriptions>
    </template>
    <template v-else>
      <div class="flex h-[120px] items-center justify-center text-gray-400">
        请在左侧选择菜单查看详情
      </div>
    </template>
  </Card>
</template>

<style lang="less" scoped>
.menu-detail-card {
  flex-shrink: 0;

  :deep(.ant-card-body) {
    padding: 16px;
  }
}
</style>
