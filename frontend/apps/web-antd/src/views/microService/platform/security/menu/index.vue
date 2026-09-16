<script setup lang="ts" name="SysMenuPage">
/**
 * 菜单管理页面
 * 左侧：菜单树 + 搜索
 * 右上：菜单详情（只读展示）
 * 右下：按钮权限列表
 */
import type { TreeProps } from 'ant-design-vue';

import type { RegisteredClient } from './api';

import { createVNode, nextTick, onMounted, ref, watch } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { filterTree } from '@vben/utils';

import {
  AppstoreOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue';
import {
  Button,
  Card,
  InputSearch,
  Modal,
  notification,
  Tooltip,
  Tree,
} from 'ant-design-vue';

import * as api from './api';
import ButtonPanel from './button-panel.vue';
import MenuDetail from './menu-detail.vue';
import { menuForm } from './scheme';

/** 应用列表 */
const appList = ref<RegisteredClient[]>([]);
/** 当前选中的应用 */
const currentClientId = ref<string>('');
/** 原始菜单树数据 */
const originTreeData = ref<any[]>([]);
/** 过滤后的菜单树数据 */
const treeData = ref<TreeProps['treeData']>([]);
/** 展开的节点 */
const expandedKeys = ref<string[]>([]);
/** 搜索关键词 */
const searchValue = ref('');
/** 选中的菜单 */
const selectedMenu = ref<any>(null);
/** 选中的节点 key */
const selectedKeys = ref<string[]>([]);
/** 按钮权限面板引用 */
const buttonPanelRef = ref();
/** 是否编辑模式 */
const isEdit = ref(false);
/** 菜单表单 */
const [MenuForm, menuFormRef] = menuForm(onFormSubmit);
/** 抽屉标题 */
const drawerTitle = ref('新增菜单');
/** 编辑抽屉 */
const [EditDrawer, editDrawerApi] = useVbenDrawer({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = editDrawerApi.getData<{ title: string }>();
      if (data?.title) {
        drawerTitle.value = data.title;
      }
    }
  },
  async onConfirm() {
    // 校验表单
    const { valid } = await menuFormRef.validate();
    if (!valid) return false; // 校验失败，阻止关闭
    // 校验通过，提交表单
    await menuFormRef.submitForm();
  },
});

/** 监听搜索变化，过滤树 */
watch(searchValue, (val) => {
  // 如果原始数据为空，不执行过滤
  if (!originTreeData.value || originTreeData.value.length === 0) {
    return;
  }

  if (val) {
    treeData.value = filterTree(originTreeData.value, (node) =>
      node.title?.toLowerCase().includes(val.toLowerCase()),
    );
    // 展开所有匹配的父节点
    expandedKeys.value = getAllKeys(treeData.value as any[]);
  } else {
    treeData.value = originTreeData.value;
    // 恢复默认展开
    expandedKeys.value = originTreeData.value
      .filter((item: any) => item.parentId === '0')
      .map((item: any) => item.id);
  }
});

/** 获取所有节点 key */
function getAllKeys(data: any[]): string[] {
  const keys: string[] = [];
  for (const item of data) {
    keys.push(item.id);
    if (item.children?.length) {
      keys.push(...getAllKeys(item.children));
    }
  }
  return keys;
}

/** 表单提交 */
function onFormSubmit(values: Record<string, any>) {
  // 组装 meta 对象（仅 Vben 特有配置，后端会自动处理 title/icon/keepAlive/hideInMenu）
  const meta: Record<string, any> = {
    activeIcon: values.activeIcon,
    hideChildrenInMenu: values.hideChildrenInMenu,
    hideInBreadcrumb: values.hideInBreadcrumb,
    hideInTab: values.hideInTab,
    affixTab: values.affixTab,
    badge: values.badge,
    badgeType: values.badgeType,
    badgeVariants: values.badgeVariants,
  };
  // 移除 meta 中的 undefined/null/空值
  Object.keys(meta).forEach((key) => {
    if (meta[key] === undefined || meta[key] === null || meta[key] === '') {
      delete meta[key];
    }
  });

  // 组装提交参数（与后端 ResourceSaveReq 对应）
  const params: Record<string, any> = {
    id: values.id,
    clientId: values.clientId,
    type: values.type,
    parentId: values.parentId || 0,
    title: values.title,
    path: values.path,
    component: values.component,
    icon: values.icon,
    permission: values.permission,
    sequence: values.sequence ?? 0,
    status: values.status ?? true,
    keepAlive: values.keepAlive,
    visible: !values.hideInMenu, // 后端用 visible，取反 hideInMenu
    shared: values.shared,
    description: values.description,
    meta: Object.keys(meta).length > 0 ? meta : null, // JSON 对象
  };

  api.SaveOrUpdate(params).then(() => {
    notification.success({ duration: 3, message: '保存成功' });
    editDrawerApi.close();
    loadMenu();
  });
}

/** 切换应用 */
function handleAppChange(clientId: any) {
  if (!clientId) return;
  currentClientId.value = clientId as string;
  // 清空所有状态
  selectedMenu.value = null;
  selectedKeys.value = [];
  expandedKeys.value = [];
  searchValue.value = '';
  treeData.value = [];
  originTreeData.value = [];
  // 重新加载菜单
  loadMenu();
}

/** 加载菜单树 */
async function loadMenu() {
  if (!currentClientId.value) return;
  // 根据当前选中的应用加载菜单
  const ret = await api.GetMenuTreeByClientId(currentClientId.value);
  const menuList = (ret as any[]) || [];
  originTreeData.value = menuList;
  treeData.value = menuList;
  // 只展开第一个顶级节点
  const firstRoot = menuList.find((item) => item.parentId === '0');
  expandedKeys.value = firstRoot?.id ? [firstRoot.id] : [];
  // 如果有选中的菜单，刷新选中状态
  if (selectedMenu.value) {
    const id = selectedMenu.value.id;
    const findNode = (nodes: any[]): any => {
      for (const node of nodes) {
        if (node.id === id) return node;
        if (node.children) {
          const found = findNode(node.children);
          if (found) return found;
        }
      }
      return null;
    };
    const node = findNode(ret as any[]);
    if (node) {
      selectedMenu.value = node;
    } else {
      selectedMenu.value = null;
      selectedKeys.value = [];
    }
  }
}

/** 选中菜单节点 */
async function handleSelect(_keys: any[], event: any) {
  if (!event.selected) return;
  const node = event.selectedNodes[0];
  selectedMenu.value = node;
  selectedKeys.value = [node.id];

  // 等待 DOM 更新后刷新按钮权限表格
  await nextTick();
  buttonPanelRef.value?.setParentIdAndRefresh(node.id);
}

/** 添加目录 */
function handleAddDirectory() {
  isEdit.value = false;
  editDrawerApi.setData({ title: '新增目录' });
  menuFormRef.resetForm();
  menuFormRef.resetValidate();
  menuFormRef.setValues({
    type: 'directory',
    parentId: '0',
    component: 'BasicLayout',
    clientId: currentClientId.value,
    clientIdDisplay: currentClientId.value,
  });
  editDrawerApi.open();
}

/** 添加子菜单 */
function handleAddChild(node: any, e: Event) {
  e.stopPropagation();
  isEdit.value = false;
  editDrawerApi.setData({ title: '新增菜单' });
  menuFormRef.resetForm();
  menuFormRef.resetValidate();
  menuFormRef.setValues({
    type: 'menu',
    parentId: node.id,
    clientId: currentClientId.value,
    clientIdDisplay: currentClientId.value,
  });
  editDrawerApi.open();
}

/** 编辑菜单 */
function handleEdit() {
  if (!selectedMenu.value) return;
  isEdit.value = true;
  editDrawerApi.setData({ title: '编辑菜单' });
  menuFormRef.resetForm();
  menuFormRef.resetValidate();
  const node = selectedMenu.value;
  // meta 可能是对象或 JSON 字符串
  const meta =
    typeof node.meta === 'string'
      ? JSON.parse(node.meta || '{}')
      : node.meta || {};
  menuFormRef.setValues({
    id: node.id,
    clientId: node.clientId || currentClientId.value,
    clientIdDisplay: node.clientId || currentClientId.value,
    type: node.type,
    parentId: node.parentId,
    title: node.title,
    path: node.path,
    component: node.component,
    icon: node.icon,
    permission: node.permission,
    sequence: node.sequence,
    status: node.status,
    keepAlive: node.keepAlive,
    visible: node.visible,
    shared: node.shared,
    description: node.description,
    // 从 meta 中解析 Vben 特有字段
    hideInMenu: node.visible === false, // visible 取反
    activeIcon: meta.activeIcon,
    hideChildrenInMenu: meta.hideChildrenInMenu,
    hideInBreadcrumb: meta.hideInBreadcrumb,
    hideInTab: meta.hideInTab,
    affixTab: meta.affixTab,
    badge: meta.badge,
    badgeType: meta.badgeType,
    badgeVariants: meta.badgeVariants,
  });
  editDrawerApi.open();
}

/** 删除菜单 */
function handleDelete(node?: any) {
  const target = node || selectedMenu.value;
  if (!target) return;
  Modal.confirm({
    icon: createVNode(ExclamationCircleOutlined),
    title: '确认删除',
    content: `确定删除「${target.title}」？同时会级联删除子节点以及相关资源数据`,
    okType: 'danger',
    onOk: async () => {
      await api.DelObj(target.id);
      notification.success({ message: '删除成功', duration: 3 });
      if (selectedMenu.value?.id === target.id) {
        selectedMenu.value = null;
        selectedKeys.value = [];
      }
      await loadMenu();
    },
  });
}

/** 加载应用列表 */
async function loadAppList() {
  try {
    const ret = await api.GetAppList();
    appList.value = ret || [];
    // 默认选中第一个应用
    if (appList.value.length > 0 && !currentClientId.value) {
      currentClientId.value = appList.value[0]!.clientId;
      loadMenu();
    }
  } catch (error) {
    console.error('加载应用列表失败', error);
  }
}

onMounted(loadAppList);
</script>

<template>
  <Page content-class="menu-page-content" :auto-content-height="true">
    <!-- 左侧：应用列表 -->
    <Card class="app-list-card" :bordered="false">
      <template #title>
        <span class="font-medium">应用列表</span>
      </template>
      <div class="app-list">
        <div
          v-for="app in appList"
          :key="app.clientId"
          class="app-item"
          :class="{ 'app-item--active': currentClientId === app.clientId }"
          @click="handleAppChange(app.clientId)"
        >
          <AppstoreOutlined class="app-item__icon" />
          <span class="app-item__name">{{ app.clientName }}</span>
        </div>
      </div>
    </Card>

    <!-- 中间：菜单树 -->
    <Card class="menu-tree-card" :bordered="false">
      <template #title>
        <span class="font-medium">菜单列表</span>
      </template>
      <template #extra>
        <div class="flex items-center gap-1">
          <Tooltip title="新增目录">
            <Button type="text" size="small" @click="handleAddDirectory">
              <template #icon><PlusOutlined /></template>
            </Button>
          </Tooltip>
          <Tooltip title="刷新">
            <Button type="text" size="small" @click="loadMenu">
              <template #icon><ReloadOutlined /></template>
            </Button>
          </Tooltip>
        </div>
      </template>

      <!-- 搜索框 -->
      <InputSearch
        v-model:value="searchValue"
        placeholder="请输入菜单名称"
        allow-clear
        class="mb-3"
      />

      <!-- 菜单树 -->
      <div class="menu-tree-content">
        <Tree
          v-if="treeData && treeData.length > 0"
          :key="currentClientId"
          v-model:expanded-keys="expandedKeys"
          v-model:selected-keys="selectedKeys"
          :field-names="{ key: 'id', title: 'title' }"
          :tree-data="treeData"
          block-node
          @select="handleSelect"
        >
          <template #title="{ title, data }">
            <div class="tree-node">
              <span
                class="tree-node__title"
                :class="{
                  'text-primary': searchValue && title?.includes(searchValue),
                }"
              >
                {{ title }}
              </span>
              <span class="tree-node__actions">
                <Tooltip title="添加子菜单">
                  <PlusOutlined
                    class="action-icon"
                    @click="(e) => handleAddChild(data, e)"
                  />
                </Tooltip>
                <Tooltip title="删除">
                  <DeleteOutlined
                    class="action-icon action-icon--danger"
                    @click.stop="handleDelete(data)"
                  />
                </Tooltip>
              </span>
            </div>
          </template>
        </Tree>
        <div v-else class="flex h-[200px] items-center justify-center text-gray-400">
          暂无菜单数据
        </div>
      </div>
    </Card>

    <!-- 右侧：详情区 -->
    <div class="menu-content-area">
      <!-- 菜单详情 -->
      <MenuDetail
        :menu="selectedMenu"
        @edit="handleEdit"
        @delete="handleDelete()"
      />

      <!-- 按钮权限面板 -->
      <ButtonPanel ref="buttonPanelRef" :menu="selectedMenu" />
    </div>

    <!-- 编辑抽屉 -->
    <EditDrawer class="w-full max-w-[800px]" :title="drawerTitle">
      <MenuForm class="mx-4" layout="horizontal" />
    </EditDrawer>
  </Page>
</template>

<style lang="less" scoped>
/* 页面整体布局：三栏 */
:deep(.menu-page-content) {
  display: flex;
  gap: 12px;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

/* 左侧应用列表 */
.app-list-card {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  width: 200px;
  overflow: hidden;

  :deep(.ant-card-head) {
    min-height: 42px;
    padding: 0 12px;
  }

  :deep(.ant-card-body) {
    flex: 1;
    padding: 8px;
    // overflow-y: auto;
    scrollbar-gutter: stable;
  }
}

.app-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.app-item {
  position: relative;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  font-size: 14px;
  color: var(--foreground);
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;

  &::before {
    position: absolute;
    top: 50%;
    left: 0;
    width: 3px;
    height: 0;
    content: '';
    background: var(--primary);
    border-radius: 0 2px 2px 0;
    transform: translateY(-50%);
    transition: height 0.2s ease;
  }

  &:hover {
    background: var(--accent);
  }

  &--active,
  &--active:hover {
    font-weight: 600;
    color: hsl(var(--primary));
    background: hsl(var(--primary) / 15%) !important;

    &::before {
      height: 60%;
    }

    .app-item__icon {
      color: hsl(var(--primary));
      transform: scale(1.1);
    }
  }

  &__icon {
    font-size: 20px;
    color: var(--muted-foreground);
    transition: all 0.2s ease;
  }

  &__name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

/* 中间菜单树 */
.menu-tree-card {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  width: 320px;
  min-width: 280px;
  overflow: hidden;

  :deep(.ant-card-body) {
    display: flex;
    flex: 1;
    flex-direction: column;
    padding: 12px;
    overflow: hidden;
  }
}

.menu-tree-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scroll-behavior: smooth;
  scrollbar-gutter: stable;

  :deep(.ant-tree-treenode) {
    width: 100%;
    padding: 4px 0;

    &:hover {
      background: var(--accent);
      border-radius: 4px;
    }
  }

  :deep(.ant-tree-node-content-wrapper) {
    flex: 1;
    min-width: 0;
  }
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;

  &__title {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__actions {
    display: none;
    gap: 8px;
    margin-left: 8px;
  }

  &:hover &__actions {
    display: flex;
  }
}

.action-icon {
  padding: 4px;
  font-size: 14px;
  color: var(--primary);
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;

  &:hover {
    background: var(--primary-100);
  }

  &--danger {
    color: #ff4d4f;

    &:hover {
      background: rgb(255 77 79 / 10%);
    }
  }
}

.menu-content-area {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  overflow: hidden;
}
</style>
