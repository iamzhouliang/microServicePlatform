/**
 * 网关路由 API
 */
import { defHttp } from '#/api/request';

const BASE_URL = '/gateway/rules/routes';

/** 获取路由列表 */
export const GetList = (query: any) => defHttp.get(BASE_URL, { params: query });

/** 新增/更新路由 */
export const SaveOrUpdate = (data: any) => defHttp.post(BASE_URL, { data });

/** 删除路由 */
export const DelObj = (id: any) => defHttp.delete(`${BASE_URL}/${id}`);

/** 上线/下线路由 */
export const ServiceStatus = (id: any, status: boolean) =>
  defHttp.request(`${BASE_URL}/${id}/${status}`, { method: 'patch' });
