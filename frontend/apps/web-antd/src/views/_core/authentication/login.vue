<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { AuthenticationLogin, z } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { loadTenantSetting, thirdAuthGitee } from '#/api';
import { useCaptchaModal } from '#/components/captcha';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();
const loginRef = ref();
const loginPropsRef = ref();
const { open: openCaptcha } = useCaptchaModal();

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: '请输入租户编码',
      },
      dependencies: {
        disabled: true,
        triggerFields: [''],
      },
      fieldName: 'desc',
    },
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: '租户编码',
      },
      dependencies: {
        show: true,
        triggerFields: [''],
      },
      fieldName: 'tenantCode',
      label: '租户编码',
      rules: z
        .string()
        .max(6, { message: '租户编码不能为空' })
        .min(4, { message: '租户编码不能为空' }),
    },
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      defaultValue: 'admin',
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      defaultValue: '123456',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
  ];
});

const route = useRoute();
const urlParams = new URLSearchParams(window.location.search);

const thirdAuth = reactive({
  accountId: route.query.accountId || urlParams.get('accountId'),
  platform: route.query.platform || urlParams.get('platform'),
  tenantCode: route.query.tenantCode || urlParams.get('tenantCode'),
});

onMounted(async () => {
  // 第三方登录回调处理
  if (thirdAuth.accountId && thirdAuth.platform) {
    await authStore.authLogin(
      {
        tenantCode: thirdAuth.tenantCode,
        username: thirdAuth.accountId,
        password: thirdAuth.platform,
        loginType: thirdAuth.platform,
      },
      () => {
        const newUrl =
          window.location.origin +
          window.location.pathname +
          window.location.hash;
        window.history.replaceState({}, '', newUrl);
        window.location.href = newUrl;
      },
    );
    return;
  }

  // 加载租户配置
  const formApi = loginRef.value.getFormApi();
  await loadTenantSetting({}).then((ret) => {
    loginPropsRef.value = ret;
    if (ret.tenantCode) {
      formApi.updateSchema([
        {
          fieldName: 'tenantCode',
          dependencies: {
            show: false,
          },
        },
      ]);
      formApi.setFieldValue('tenantCode', ret.tenantCode);
    }
  });
});

/**
 * 点击登录按钮 - 弹出验证码
 */
async function handleSubmit(
  params: any,
  onSuccess?: () => Promise<void> | void,
) {
  // GitEE OAuth 是独立的第三方登录，保留作为可选 OAuth 入口
const thirdPartyOAuthInterceptor = import.meta.env.VITE_GITEE_INTERCEPT;
  if (
    thirdPartyOAuthInterceptor === true ||
    thirdPartyOAuthInterceptor === 'true'
  ) {
    thirdAuthGitee().then((ret: any) => {
      window.location.href = ret.authorizeUrl;
    });
    return;
  }

  try {
    // 弹出验证码弹窗，验证成功后返回 captchaKey
    const code = await openCaptcha(params.username);
    // 调用登录接口
    authStore.authLogin(
      {
        ...params,
        code,
      },
      onSuccess,
    );
  } catch {
    // 用户取消验证，不做处理
  }
}
</script>

<template>
  <AuthenticationLogin
    ref="loginRef"
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    :show-code-login="loginPropsRef?.showCodeLogin"
    :show-forget-password="loginPropsRef?.showForgetPassword"
    :show-qrcode-login="loginPropsRef?.showQrcodeLogin"
    :show-register="loginPropsRef?.showRegister"
    :show-remember-me="loginPropsRef?.showRememberMe"
    :show-third-party-login="loginPropsRef?.showThirdPartyLogin"
    :sub-title="loginPropsRef?.subTitle"
    :title="loginPropsRef?.title"
    @submit="handleSubmit"
  />
</template>
