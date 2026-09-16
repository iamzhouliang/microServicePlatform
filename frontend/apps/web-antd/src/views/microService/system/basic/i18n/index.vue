<script setup lang="ts" name="I18nDataPage">
import { onMounted } from 'vue';

import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';
import { useFs } from '@fast-crud/fast-crud';
import { FormItemRest } from 'ant-design-vue/es/form';

import createCrudOptions from './crud';

/** 预设语言选项 */
const localeOptions = [
  { value: 'zh_CN', label: '简体中文' },
  { value: 'en_US', label: 'English (US)' },
  { value: 'ja_JP', label: '日本語' },
  { value: 'ko_KR', label: '한국어' },
  { value: 'fr_FR', label: 'Français' },
  { value: 'de_DE', label: 'Deutsch' },
  { value: 'es_ES', label: 'Español' },
  { value: 'pt_BR', label: 'Português' },
  { value: 'ru_RU', label: 'Русский' },
];

const { crudBinding, crudRef, crudExpose } = useFs({
  createCrudOptions,
  permission: 'sys:i18n',
} as any);

onMounted(() => {
  crudExpose.doRefresh();
});

/** 添加语言条目 */
function addLanguage(form: any) {
  if (!form.languages) {
    form.languages = [];
  }
  form.languages.push({ locale: 'zh_CN', message: '' });
}

/** 删除语言条目 */
function removeLanguage(languages: any[], index: number) {
  languages.splice(index, 1);
}

/** 获取已选语言，用于禁用已选项 */
function getSelectedLocales(languages: any[], currentIndex: number) {
  return languages
    .filter((_, i) => i !== currentIndex)
    .map((item) => item.locale);
}
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding">
      <template #form_languages="scope">
        <FormItemRest>
          <div class="language-list">
            <div
              v-for="(item, index) in scope.form.languages"
              :key="index"
              class="language-item"
            >
              <a-select
                v-model:value="item.locale"
                :disabled="scope.mode === 'view'"
                placeholder="选择语言"
                style="width: 160px"
                :options="
                  localeOptions.map((opt) => ({
                    ...opt,
                    disabled: getSelectedLocales(
                      scope.form.languages,
                      index,
                    ).includes(opt.value),
                  }))
                "
              />
              <a-input
                v-model:value="item.message"
                :disabled="scope.mode === 'view'"
                placeholder="请输入翻译文本"
                style="flex: 1"
              />
              <a-button
                v-if="scope.mode !== 'view'"
                type="text"
                danger
                @click="removeLanguage(scope.form.languages, index)"
              >
                <DeleteOutlined />
              </a-button>
            </div>
          </div>
          <a-button
            v-if="scope.mode !== 'view'"
            type="dashed"
            block
            @click="addLanguage(scope.form)"
          >
            <PlusOutlined />
            添加语言
          </a-button>
        </FormItemRest>
      </template>
    </fs-crud>
  </fs-page>
</template>

<style scoped>
.language-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.language-item {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
