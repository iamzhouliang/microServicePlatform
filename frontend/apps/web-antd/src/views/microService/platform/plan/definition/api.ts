import { defHttp } from '#/api/request';

/** 分页查询套餐列表 */
export function GetList(query: any) {
  return defHttp.get(`/iam/plan-definitions/page`, { params: query });
}

/** 获取套餐详情（含已授权资源ID） */
export async function GetObj(id: string) {
  return await defHttp.get(`/iam/plan-definitions/${id}/detail`);
}

/** 新增套餐 */
export function AddObj(form: any) {
  return defHttp.post(`/iam/plan-definitions`, form);
}

/** 更新套餐 */
export function UpdateObj(form: any) {
  return defHttp.put(`/iam/plan-definitions/${form.id}`, form);
}

/** 删除套餐 */
export function DelObj(id: string) {
  return defHttp.delete(`/iam/plan-definitions/${id}`);
}
