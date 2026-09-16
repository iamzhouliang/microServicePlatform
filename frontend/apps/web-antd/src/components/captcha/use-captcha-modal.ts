/**
 * 验证码弹窗 Hook
 * 通过函数调用方式弹出验证码，验证成功后返回 token
 */
import { createApp, h, onMounted, ref } from 'vue';

import { Modal } from 'ant-design-vue';

import TianaiSliderCaptcha from './tianai-slider-captcha.vue';

/** 注入全局样式 */
const STYLE_ID = 'captcha-modal-global-style';
function injectGlobalStyle() {
  if (document.querySelector(`#${STYLE_ID}`)) return;

  const style = document.createElement('style');
  style.id = STYLE_ID;
  style.textContent = `
    .captcha-modal-wrap .ant-modal-content {
      border-radius: 16px !important;
      overflow: hidden;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25) !important;
    }
    .captcha-modal-wrap .ant-modal-body {
      padding: 0 !important;
    }
    .captcha-modal-wrap .ant-modal-close {
      top: 16px !important;
      right: 16px !important;
      width: 32px !important;
      height: 32px !important;
      border-radius: 50% !important;
      background: #f3f4f6 !important;
      transition: all 0.2s ease !important;
    }
    .captcha-modal-wrap .ant-modal-close:hover {
      background: #e5e7eb !important;
      transform: rotate(90deg);
    }
    .captcha-modal-wrap .ant-modal-close-x {
      font-size: 14px !important;
      line-height: 32px !important;
      width: 32px !important;
      height: 32px !important;
      color: #6b7280 !important;
    }
    .captcha-modal-wrap .ant-modal-mask {
      backdrop-filter: blur(4px);
      background: rgba(0, 0, 0, 0.45) !important;
    }
  `;
  document.head.append(style);
}

export interface CaptchaModalOptions {
  /** 身份信息（用户名） */
  principal: string;
  /** 验证成功回调 */
  onSuccess?: (token: string) => void;
  /** 验证失败回调 */
  onFail?: () => void;
  /** 取消回调 */
  onCancel?: () => void;
}

/**
 * 打开验证码弹窗
 */
export function openCaptchaModal(
  options: CaptchaModalOptions,
): Promise<string> {
  // 注入全局样式
  injectGlobalStyle();

  return new Promise((resolve, reject) => {
    const container = document.createElement('div');
    document.body.append(container);

    const visible = ref(true);

    const handleSuccess = (token: string) => {
      visible.value = false;
      options.onSuccess?.(token);
      resolve(token);
      cleanup();
    };

    const handleCancel = () => {
      visible.value = false;
      options.onCancel?.();
      reject(new Error('用户取消验证'));
      cleanup();
    };

    const cleanup = () => {
      setTimeout(() => {
        if (container.parentNode) {
          container.remove();
        }
      }, 300);
    };

    const app = createApp({
      setup() {
        const animateIn = ref(false);

        onMounted(() => {
          // 延迟触发入场动画
          requestAnimationFrame(() => {
            animateIn.value = true;
          });
        });

        return () =>
          h(
            Modal,
            {
              open: visible.value,
              title: null,
              footer: null,
              width: 400,
              centered: true,
              destroyOnClose: true,
              closable: true,
              maskClosable: false,
              wrapClassName: 'captcha-modal-wrap',
              onCancel: handleCancel,
            },
            {
              default: () =>
                h(
                  'div',
                  {
                    class: 'captcha-modal-content',
                    style: {
                      padding: '24px',
                      transform: animateIn.value ? 'scale(1)' : 'scale(0.9)',
                      opacity: animateIn.value ? '1' : '0',
                      transition: 'all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)',
                    },
                  },
                  [
                    // 标题区域
                    h(
                      'div',
                      {
                        style: {
                          textAlign: 'center',
                          marginBottom: '20px',
                        },
                      },
                      [
                        h(
                          'div',
                          {
                            style: {
                              fontSize: '18px',
                              fontWeight: '600',
                              color: '#1f2937',
                              marginBottom: '4px',
                            },
                          },
                          '安全验证',
                        ),
                        h(
                          'div',
                          {
                            style: {
                              fontSize: '13px',
                              color: '#9ca3af',
                            },
                          },
                          '请完成以下验证以继续操作',
                        ),
                      ],
                    ),
                    // 验证码组件
                    h(
                      'div',
                      {
                        style: {
                          display: 'flex',
                          justifyContent: 'center',
                        },
                      },
                      [
                        h(TianaiSliderCaptcha, {
                          width: 340,
                          principal: options.principal,
                          onSuccess: handleSuccess,
                          onFail: options.onFail,
                        }),
                      ],
                    ),
                  ],
                ),
            },
          );
      },
    });

    app.mount(container);
  });
}

/**
 * 验证码 Hook
 */
export function useCaptchaModal() {
  /**
   * 打开验证码弹窗并等待验证结果
   */
  const open = (principal: string): Promise<string> => {
    return openCaptchaModal({ principal });
  };

  return {
    open,
    openCaptchaModal,
  };
}
