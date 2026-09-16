import type { UserInfo } from '@vben/types';

import type { IDS } from '#/api/common';

import { defHttp } from '#/api/request';

export interface UserProfileUpdateParams {
  birthday?: string;
  description?: string;
  email: string;
  mobile: string;
  nickname: string;
}

export interface UserPasswordUpdateParams {
  confirmPassword: string;
  currentPassword: string;
  newPassword: string;
}

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  return defHttp.get<UserInfo>('/iam/token/userinfo');
}

/**
 * 修改当前用户基本资料
 */
export async function updateUserProfileApi(data: UserProfileUpdateParams) {
  return defHttp.put('/iam/users/profile', data);
}

/**
 * 修改当前用户密码
 */
export async function updateUserPasswordApi(data: UserPasswordUpdateParams) {
  return defHttp.put('/iam/users/password', data);
}

// export async function getUserList() {
//   return defHttp.post<any>('/iam/users/list');
// }
export async function getUserByIds(values: IDS) {
  const data = Array.isArray(values[0]) ? values[0] : values;
  return defHttp.post<any>('/iam/users/ids', data);
}

/** 用户基础信息（前后端契约，与后端 User DTO 对齐） */
export interface User {
  id: number | string;
  username?: string;
  nickname?: string;
  realName?: string;
  avatar?: string;
  email?: string;
  mobile?: string;
  description?: string;
  status?: number | string;
  gender?: number | string;
  birthday?: string;
  [key: string]: any;
}
