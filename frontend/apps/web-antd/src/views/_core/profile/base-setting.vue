<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';

import { computed, onMounted, ref } from 'vue';

import { ProfileBaseSetting, z } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { updateUserProfileApi } from '#/api';
import { useAuthStore } from '#/store/auth';

const userStore = useUserStore();
const authStore = useAuthStore();
const profileBaseSettingRef = ref();

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'username',
      component: 'Input',
      label: '用户名',
      componentProps: {
        disabled: true,
        placeholder: '用户名不可修改',
      },
    },
    {
      fieldName: 'nickname',
      component: 'Input',
      label: '昵称',
      componentProps: {
        maxlength: 50,
        placeholder: '请输入昵称',
        showCount: true,
      },
      rules: z.string().min(1, '请输入昵称').max(50, '昵称不能超过50个字符'),
    },
    {
      fieldName: 'email',
      component: 'Input',
      label: '邮箱',
      componentProps: {
        maxlength: 50,
        placeholder: '请输入邮箱',
        showCount: true,
      },
      rules: z
        .string()
        .max(50, '邮箱不能超过50个字符')
        .email('邮箱格式不正确'),
    },
    {
      fieldName: 'mobile',
      component: 'Input',
      label: '手机号',
      componentProps: {
        placeholder: '请输入手机号',
      },
      rules: z.string().regex(/^1\d{10}$/, '手机号格式不正确'),
    },
    {
      fieldName: 'description',
      component: 'Textarea',
      label: '个人简介',
      componentProps: {
        placeholder: '请输入个人简介',
        rows: 4,
        maxlength: 200,
        showCount: true,
      },
    },
  ];
});

/** 初始化表单数据 */
onMounted(() => {
  const info = userStore.userInfo;
  if (info) {
    profileBaseSettingRef.value?.getFormApi()?.setValues({
      username: info.username,
      nickname: info.nickname,
      email: info.email,
      mobile: info.mobile,
      description: info.description,
    });
  }
});

/** 提交更新 */
async function handleSubmit(values: Record<string, any>) {
  try {
    await updateUserProfileApi({
      nickname: values.nickname,
      email: values.email,
      mobile: values.mobile,
      description: values.description,
    });
    await authStore.fetchUserInfo();
    message.success('个人信息更新成功');
  } catch {
    message.error('更新失败');
  }
}
</script>

<template>
  <ProfileBaseSetting
    ref="profileBaseSettingRef"
    class="w-full min-w-[420px] max-w-[820px]"
    :form-schema="formSchema"
    @submit="handleSubmit"
  />
</template>

<style scoped>
.base-setting :deep(.vben-form) {
  --vben-form-label-width: 80px;
}

.base-setting :deep(.ant-input),
.base-setting :deep(.ant-input-affix-wrapper),
.base-setting :deep(.ant-select-selector),
.base-setting :deep(textarea.ant-input) {
  border-radius: 6px;
}

.base-setting :deep(.vben-button) {
  min-width: 120px;
  border-radius: 6px;
}
</style>
