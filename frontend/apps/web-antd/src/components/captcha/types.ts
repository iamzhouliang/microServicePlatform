/**
 * tianai-captcha 滑块验证码类型定义
 * 文档：http://doc.captcha.tianai.cloud/
 */

/** 验证码生成响应 - tianai-captcha 接口格式 */
export interface CaptchaGenerateResponse {
  /** 验证码ID */
  id: string;
  /** 验证码类型: SLIDER, ROTATE, CONCAT 等 */
  type: string;
  /** 背景图（base64，可能带或不带 data:image 前缀） */
  backgroundImage: string;
  /** 滑块/模板图（base64，可能带或不带 data:image 前缀） */
  templateImage: string;
  /** 背景图宽度 */
  backgroundImageWidth: number;
  /** 背景图高度 */
  backgroundImageHeight: number;
  /** 模板图宽度 */
  templateImageWidth: number;
  /** 模板图高度 */
  templateImageHeight: number;
  /** 背景图类型标签 */
  backgroundImageTag?: string;
  /** 模板图类型标签 */
  templateImageTag?: string;
  /** 扩展数据，包含 randomY 等信息 */
  data?: {
    /** 滑块Y轴位置 */
    randomY?: number;
    /** 其他扩展字段 */
    [key: string]: any;
  };
}

/** 验证码校验参数 - 对应后端 CaptchaReq */
export interface CaptchaCheckParams {
  /** 验证码ID */
  id: string;
  /** 滑块轨迹数据 */
  captchaTrack: ImageCaptchaTrack;
  /** 身份信息（用户名等） */
  principal: string;
}

/** 滑块轨迹数据 - 对应后端 ImageCaptchaTrack */
export interface ImageCaptchaTrack {
  /** 背景图宽度 */
  bgImageWidth: number;
  /** 背景图高度 */
  bgImageHeight: number;
  /** 模板图宽度 */
  templateImageWidth: number;
  /** 模板图高度 */
  templateImageHeight: number;
  /** 滑动开始时间 */
  startTime: number;
  /** 滑动结束时间 */
  stopTime: number;
  /** 滑块最终X位置 */
  left: number;
  /** 滑块最终Y位置 */
  top: number;
  /** 滑动轨迹 */
  trackList: TrackPoint[];
  /** 扩展数据 */
  data?: any;
}

/** 轨迹点 - 对应后端 Track */
export interface TrackPoint {
  /** X坐标 */
  x: number;
  /** Y坐标 */
  y: number;
  /** 时间戳 */
  t: number;
  /** 类型：move/down/up */
  type: string;
}

/** 验证码校验响应 - 对应后端返回 */
export interface CaptchaCheckResponse {
  /** 临时Token（验证成功后用于登录） */
  tmpToken: string;
  /** 签发时间 */
  issueTime: string;
  /** 过期时间 */
  expireTime: string;
  /** 有效期（秒） */
  period: number;
}

/** 组件Props */
export interface TianaiSliderCaptchaProps {
  /** 宽度 */
  width?: number;
  /** 高度 */
  height?: number;
  /** 身份信息（用户名），校验时需要 */
  principal?: string;
}

/** 组件Emits */
export interface TianaiSliderCaptchaEmits {
  /** 验证成功 */
  (e: 'success', captchaKey: string): void;
  /** 验证失败 */
  (e: 'fail'): void;
}
