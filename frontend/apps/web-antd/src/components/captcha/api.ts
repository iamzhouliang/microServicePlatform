/**
 * tianai-captcha 验证码接口
 */
import type {
  CaptchaCheckParams,
  CaptchaCheckResponse,
  CaptchaGenerateResponse,
} from './types';

import { requestClient } from '#/api/request';

const BASE_URL = '/iam/captcha';

/** 生成验证码 */
export function generateCaptcha() {
  return requestClient.get<CaptchaGenerateResponse>(`${BASE_URL}/slider`);
}

/** 校验验证码 */
export function checkCaptcha(params: CaptchaCheckParams) {
  return requestClient.post<CaptchaCheckResponse>(
    `${BASE_URL}/slider/check`,
    params,
  );
}
