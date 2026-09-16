import { defHttp } from '#/api/request';

/**
 * 应用（注册客户端）类型定义
 */
export interface RegisteredClient {
  /** 主键ID */
  id: string;
  /** 客户端名称 */
  clientName: string;
  /** 客户端ID */
  clientId: string;
  /** 客户端ID生效时间 */
  clientIdIssuedAt?: string;
  /** 客户端秘钥 */
  clientSecret?: string;
  /** 秘钥失效时间 */
  clientSecretExpiresAt?: string;
  /** 授权类型 */
  grantTypes?: string[];
  /** 重定向地址 */
  redirectUris?: string;
  /** 退出后重定向地址 */
  postLogoutRedirectUris?: string;
  /** 授权范围 */
  scopes?: string[];
  /** 状态 */
  status?: boolean;
  /** accessToken 有效时长(分钟) */
  accessTokenTimeToLive?: number;
  /** refreshToken 有效时长(分钟) */
  refreshTokenTimeToLive?: number;
  /** 授权码 有效时长(分钟) */
  authorizationCodeTimeToLive?: number;
  /** 设备码 有效时长(分钟) */
  deviceCodeTimeToLive?: number;
}

/**
 * 获取应用（注册客户端）列表
 */
export function GetAppList(): Promise<RegisteredClient[]> {
  return defHttp.request<RegisteredClient[]>('/iam/registered-client/list', {
    method: 'get',
  });
}
