/**
 * 网关黑名单 API
 */
import { defHttp } from '#/api/request';

const BASE_URL = '/gateway/rules/blacklist';

/** 获取黑名单列表 */
export const GetList = (query: any) => defHttp.get(BASE_URL, { params: query });

/** 新增黑名单 */
export const AddObj = (data: any) => defHttp.post(BASE_URL, { data });

/** 更新黑名单 */
export const UpdateObj = (data: any) => defHttp.post(BASE_URL, { data });

/** 删除黑名单 */
export const DelObj = (id: any) => defHttp.delete(`${BASE_URL}/${id}`);
