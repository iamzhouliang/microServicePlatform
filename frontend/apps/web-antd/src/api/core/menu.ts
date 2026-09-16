import type { RouteRecordStringComponent } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户所有菜单
 */
export async function getAllMenusApi(params: any) {
  return requestClient.get<RouteRecordStringComponent[]>(
    '/iam/resources/router',
    { params },
  );
}

/**
 * 获取用户所有菜单
 */
export async function getResourceTree() {
  return requestClient.get<RouteRecordStringComponent[]>(
    '/iam/resources/trees',
  );
}
