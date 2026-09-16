<script lang="ts" setup>
import type {
  WorkbenchProjectItem,
  WorkbenchQuickNavItem,
} from '@vben/common-ui';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  AnalysisChartCard,
  WorkbenchHeader,
  WorkbenchProject,
  WorkbenchQuickNav,
  WorkbenchTodo,
  WorkbenchTrends,
} from '@vben/common-ui';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';
import { openWindow } from '@vben/utils';

import AnalyticsVisitsSource from '../analytics/analytics-visits-source.vue';
import {
  defaultTodoItems,
  projectItems,
  quickNavItems,
  trendItems,
} from './data';

const userStore = useUserStore();
const router = useRouter();
const todoItems = ref([...defaultTodoItems]);

/** 导航跳转 - 支持内部路由和外部链接 */
function handleNavigation(nav: WorkbenchProjectItem | WorkbenchQuickNavItem) {
  const { url, title } = nav;
  if (!url) {
    console.warn(`导航项 [${title}] 未配置URL`);
    return;
  }
  // 外部链接
  if (url.startsWith('http')) {
    openWindow(url);
    return;
  }
  // 内部路由
  if (url.startsWith('/')) {
    router.push(url).catch((error) => {
      console.error(`导航失败: ${title} -> ${url}`, error);
    });
  }
}
</script>

<template>
  <div class="p-3">
    <!-- 头部欢迎区域 -->
    <WorkbenchHeader
      :avatar="userStore.userInfo?.avatar || preferences.app.defaultAvatar"
    >
      <template #title>
        早安, {{ userStore.userInfo?.realName }}, 开始您一天的工作吧！
      </template>
      <template #description>今日晴，20℃ - 32℃！</template>
    </WorkbenchHeader>

    <!-- 主内容区域 -->
    <div class="mt-2 flex flex-col lg:flex-row">
      <!-- 左侧区域 -->
      <div class="mr-4 w-full lg:w-3/5">
        <WorkbenchProject
          :items="projectItems"
          title="项目"
          @click="handleNavigation"
        />
        <WorkbenchTrends :items="trendItems" class="mt-5" title="最新动态" />
      </div>

      <!-- 右侧区域 -->
      <div class="w-full lg:w-2/5">
        <WorkbenchQuickNav
          :items="quickNavItems"
          class="mt-5 lg:mt-0"
          title="快捷导航"
          @click="handleNavigation"
        />
        <WorkbenchTodo :items="todoItems" class="mt-5" title="待办事项" />
      </div>
    </div>
  </div>
</template>
