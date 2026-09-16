import { defHttp } from '#/api/request';

export function GetResourceList(query: any) {
  return defHttp.request('/iam/resources/page', {
    method: 'get',
    params: query,
  });
}
export function GetList(data: any) {
  return defHttp.post('/iam/roles/page', data);
}
export function GetObj(id: any) {
  return defHttp.get(`/iam/roles/${id}/detail`);
}
export function AddObj(obj: any) {
  return defHttp.post('/iam/roles/create', obj);
}

export function UpdateObj(data: any) {
  return defHttp.put(`/iam/roles/${data.id}`, data);
}

export function DelObj(id: string) {
  return defHttp.delete(`/iam/roles/${id}`);
}

export function getUserByRoleId(roleId: string) {
  return defHttp.get(`/iam/roles/${roleId}/users`);
}
export function getRolePermissions(roleId: string) {
  return defHttp.get(`/iam/roles/${roleId}/permissions`);
}
export function assignUser(obj: any) {
  return defHttp.put(`/iam/roles/${obj.roleId}/assign-users`, obj);
}

export function assignResource(obj: any) {
  return defHttp.put(`/iam/roles/${obj.roleId}/assign-resources`, obj);
}

// ==================== 数据权限相关接口 ====================

/** 数据资源类型枚举 */
export const DataResourceType = {
  ROLE: 'ROLE',
  USER: 'USER',
  ORG: 'ORG',
  PROJECT: 'PROJECT',
  CUSTOMER: 'CUSTOMER',
  OUTLET: 'OUTLET',
} as const;

/** 权限范围类型枚举 */
export const ScopeTypeEnum = {
  SELF: 10, // 个人
  CUSTOM: 20, // 自定义
  DEPT: 30, // 本级
  DEPT_CHILDREN: 40, // 本级及子级
  ALL: 50, // 全部
} as const;

/** 权限范围类型选项 */
export const scopeTypeOptions = [
  { value: ScopeTypeEnum.ALL, label: '全部', color: 'success' },
  {
    value: ScopeTypeEnum.DEPT_CHILDREN,
    label: '本级及子级',
    color: 'processing',
  },
  { value: ScopeTypeEnum.DEPT, label: '本级', color: 'warning' },
  { value: ScopeTypeEnum.CUSTOM, label: '自定义', color: 'error' },
  { value: ScopeTypeEnum.SELF, label: '个人', color: 'default' },
];

/** 维度配置类型 */
export interface DimensionConfig {
  code: string;
  name: string;
  icon?: string;
  treeAble: boolean;
  /** 数据加载函数 */
  loadData?: () => Promise<DimensionDataItem[]>;
}

/** 维度数据项类型 */
export interface DimensionDataItem {
  id: number | string;
  name: string;
  parentId?: number | string;
  children?: DimensionDataItem[];
}

/** 数据权限响应类型 */
export interface DataPermissionResp {
  dataType: string;
  scopeType: number;
  dataIds: (number | string)[];
}

/** 数据权限分配请求类型 */
export interface DataPermissionAssignReq {
  dataType: string;
  scopeType: number;
  dataIds: (number | string)[];
}

/** 获取客户列表（静态数据） */
export function getCompanyList(): Promise<DimensionDataItem[]> {
  return Promise.resolve([
    { id: 1, name: '阿里巴巴集团' },
    { id: 2, name: '腾讯科技' },
    { id: 3, name: '字节跳动' },
    { id: 4, name: '华为技术' },
    { id: 5, name: '百度在线' },
    { id: 6, name: '京东集团' },
    { id: 7, name: '美团点评' },
    { id: 8, name: '小米科技' },
    { id: 9, name: '网易公司' },
    { id: 10, name: '滴滴出行' },
  ]);
}

/** 获取用户列表（静态数据） */
export function getUserList(): Promise<DimensionDataItem[]> {
  return Promise.resolve([
    { id: 1, name: '张三' },
    { id: 2, name: '李四' },
    { id: 3, name: '王五' },
    { id: 4, name: '赵六' },
    { id: 5, name: '钱七' },
    { id: 6, name: '孙八' },
    { id: 7, name: '周九' },
    { id: 8, name: '吴十' },
  ]);
}

/** 维度配置（写死） */
export const dimensionConfigs: DimensionConfig[] = [
  {
    code: 'company',
    name: '客户',
    icon: 'building',
    treeAble: false,
    loadData: getCompanyList,
  },
  {
    code: 'user',
    name: '用户',
    icon: 'user',
    treeAble: false,
    loadData: getUserList,
  },
];

/** 获取角色数据权限配置 */
export function getRoleDataScopes(
  roleId: number | string,
): Promise<DataPermissionResp[]> {
  return defHttp.get(`/iam/data-permissions/ROLE/${roleId}`);
}

/** 批量保存角色数据权限 */
export function saveRoleDataScopes(
  roleId: number | string,
  reqList: DataPermissionAssignReq[],
): Promise<void> {
  return defHttp.put(`/iam/data-permissions/ROLE/${roleId}`, reqList);
}

/** 删除角色数据权限 */
export function removeRoleDataScope(
  roleId: number | string,
  dataType?: string,
): Promise<void> {
  return defHttp.delete(`/iam/data-permissions/ROLE/${roleId}`, {
    params: dataType ? { dataType } : undefined,
  });
}
