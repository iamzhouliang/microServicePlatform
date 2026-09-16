import { defHttp } from '#/api/request';

// ==================== 类型定义 ====================

/** 模型类型枚举 */
export type ModelType = 'AUDIO' | 'EMBEDDING' | 'IMAGE' | 'RERANK' | 'TEXT';

/** 分页查询参数 */
export interface AiModelPageReq {
  current: number;
  size: number;
  column?: string;
  asc?: boolean;
  name?: string;
  provider?: string;
  type?: ModelType;
}

/** 列表项 */
export interface AiModelPageRep {
  id: number;
  name: string;
  type: ModelType;
  provider: string;
  baseUrl: string;
  createName: string;
  createTime: string;
}

/** 详情 */
export interface AiModelDetailRep {
  id: number;
  name: string;
  type: ModelType;
  provider: string;
  apiKey: string;
  baseUrl: string;
  variables?: Record<string, any>;
}

/** 保存参数 */
export interface AiModelSaveReq {
  name: string;
  type: ModelType;
  provider: string;
  apiKey: string;
  baseUrl?: string;
  variables?: Record<string, any>;
}

// ==================== API 接口 ====================

const BASE_URL = '/ai/models';

export const PageList = (params: AiModelPageReq) =>
  defHttp.post<AiModelPageRep>(`${BASE_URL}/page`, params);

export const GetInfo = (id: number) =>
  defHttp.get<AiModelDetailRep>(`${BASE_URL}/${id}/detail`);

export const AddObj = (data: AiModelSaveReq) =>
  defHttp.post(`${BASE_URL}/create`, data);

export const UpdateObj = (id: number, data: AiModelSaveReq) =>
  defHttp.put(`${BASE_URL}/${id}/modify`, data);

export const DelObj = (id: number) => defHttp.delete(`${BASE_URL}/${id}`);
