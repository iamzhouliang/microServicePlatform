import { defHttp } from '#/api/request';

// 从 app 模块重新导出应用相关类型和方法
export { GetAppList } from '../app/api';
export type { RegisteredClient } from '../app/api';

/**
 * 根据应用ID获取菜单树
 * @param clientId 应用ID
 */
export function GetMenuTreeByClientId(clientId: string) {
  return defHttp.request('/iam/resources/trees', {
    method: 'get',
    params: { clientId },
  });
}

export function GetResourceList(query: any) {
  return defHttp.request('/iam/resources/page', {
    method: 'get',
    params: query,
  });
}
export function SaveOrUpdate(obj: any) {
  return obj.id ? UpdateObj(obj) : AddObj(obj);
}
export function AddObj(obj: any) {
  return defHttp.request('/iam/resources/create', {
    method: 'post',
    data: obj,
  });
}

export function UpdateObj(obj: any) {
  return defHttp.request(`/iam/resources/${obj.id}/modify`, {
    method: 'put',
    data: obj,
  });
}

export function DelObj(id: any) {
  return defHttp.request(`/iam/resources/${id}`, {
    method: 'delete',
    data: { id },
  });
}

export function GetBuildStandardList(query: any) {
  return defHttp.request('/suite/dynamic_release_drag', {
    method: 'get',
    params: query,
  });
}
