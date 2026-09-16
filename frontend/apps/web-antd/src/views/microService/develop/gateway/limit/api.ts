/**
 * 网关限流 API
 */
import { defHttp } from '#/api/request';

const BASE_URL = '/gateway/rules/limits';

/** 获取限流列表 */
export const GetList = (query: any) => defHttp.get(BASE_URL, { params: query });

/** 新增限流规则 */
export const AddObj = (data: any) => defHttp.post(BASE_URL, { data });

/** 更新限流规则 */
export const UpdateObj = (data: any) =>
  defHttp.put(`${BASE_URL}/${data.id}`, { data });

/** 删除限流规则 */
export const DelObj = (id: any) => defHttp.delete(`${BASE_URL}/${id}`);
