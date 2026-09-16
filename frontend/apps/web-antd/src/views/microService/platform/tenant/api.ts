import { defHttp } from '#/api/request';

/** 获取租户设置 */
export function getTenantSetting(tenantId: any) {
  return defHttp.get(`/iam/tenants/${tenantId}/setting`);
}

/** 保存租户设置 */
export function setTenantSetting(data: any) {
  return defHttp.put(`/iam/tenants/${data.tenantId}/setting`, data);
}

/**
 * 获取租户数据源绑定配置
 * @param tenantId 租户ID
 */
export function getTenantDbBinding(tenantId: number | string) {
  return defHttp.get(`/iam/tenants/${tenantId}/db-ref`);
}

/**
 * 保存租户数据源绑定配置
 * @param data 绑定配置数据
 */
export function saveTenantDbBinding(data: {
  dbInstanceId?: number;
  isPrimary?: boolean;
  schemaName?: string;
  strategy: 'COLUMN' | 'DATABASE' | 'SCHEMA';
  tenantId: number;
}) {
  return defHttp.post(`/iam/tenants/${data.tenantId}/db-binding`, data);
}

/**
 * 删除租户数据源绑定
 * @param id 绑定记录ID
 */
export function deleteTenantDbBinding(id: number | string) {
  return defHttp.delete(`/iam/tenant-db-bindings/${id}`);
}
