<script lang="ts" setup>
/**
 * 网关路由管理
 */
import { onMounted, reactive } from 'vue';

import {
  DeleteOutlined,
  DownOutlined,
  PlusSquareOutlined,
} from '@ant-design/icons-vue';
import { useFs } from '@fast-crud/fast-crud';
import { cloneDeep } from 'lodash-es';

import createCrudOptions from './crud';
import { filters, predicates } from './data';

defineOptions({ name: 'GatewayRouteForm' });

const { crudRef, crudBinding, crudExpose } = useFs({ createCrudOptions });
const router = reactive({ predicates, filters });
const cloneDeepRouter = cloneDeep(router);

onMounted(() => crudExpose.doRefresh());

// ========== 路由条件(Predicates)操作 ==========

/** 删除路由条件 */
const removePredicate = (
  scopeItem: any,
  scopeIndex: number,
  form: any,
  key: string,
) => {
  router.predicates.push(scopeItem.name);
  form[key].splice(scopeIndex, 1);
};

/** 删除条件标签 */
const removePredicateTag = (scopeItem: any, removedTag: string) => {
  scopeItem.args = scopeItem.args.filter((tag: string) => tag !== removedTag);
};

/** 确认输入 */
const handleInputConfirm = (scopeItem: any) => {
  if (scopeItem.inputValue) {
    scopeItem.args.push(scopeItem.inputValue);
    scopeItem.args = [...new Set(scopeItem.args)];
    scopeItem.inputValue = undefined;
  }
  scopeItem.inputVisible = false;
};

/** 添加路由条件 */
const handlePredicateChange = (
  name: string,
  scopeForm: any,
  scopeKey: string,
) => {
  router.predicates.splice(router.predicates.indexOf(name), 1);
  if (!scopeForm[scopeKey]) scopeForm[scopeKey] = [];
  scopeForm[scopeKey].push({ name, args: [] });
};

/** 显示输入框 */
const showInput = (scopeItem: any) => (scopeItem.inputVisible = true);

/** 处理条件下拉数据 */
const processPredicates = (scope: any) => {
  router.predicates = cloneDeep(cloneDeepRouter).predicates;
  if (scope.form.predicates) {
    const usedNames = new Set(
      scope.form.predicates.map((item: any) => item.name),
    );
    router.predicates = router.predicates.filter(
      (item) => !usedNames.has(item),
    );
  }
};

// ========== 过滤器(Filters)操作 ==========

/** 处理过滤器下拉数据 */
const processFilters = (scope: any) => {
  router.filters = cloneDeep(cloneDeepRouter).filters;
  if (scope.form.filters) {
    const usedNames = new Set(scope.form.filters.map((item: any) => item.name));
    router.filters = router.filters.filter((item) => !usedNames.has(item.name));
  }
};

/** 添加过滤器 */
const handleFilterChange = (filter: any, scopeForm: any, scopeKey: string) => {
  const idx = router.filters.findIndex((item) => item.name === filter.name);
  if (idx !== -1) router.filters.splice(idx, 1);
  if (!scopeForm[scopeKey]) scopeForm[scopeKey] = [];
  scopeForm[scopeKey].push(filter);
};

/** 删除过滤器参数 */
const removeFilterParams = (scopeItem: any, filterIndex: number) => {
  scopeItem.args.splice(filterIndex, 1);
};

/** 删除过滤器 */
const removeFilter = (
  scopeItem: any,
  scopeIndex: number,
  form: any,
  key: string,
) => {
  router.filters.push(scopeItem);
  form[key].splice(scopeIndex, 1);
};

/** 添加过滤器参数 */
const addFilterParams = (scopeForm: any) => {
  scopeForm.args.push({
    key: `_genkey_${scopeForm.args.length + 1}`,
    value: '',
  });
};
</script>

<template>
  <fs-page class="page-layout-card">
    <fs-crud ref="crudRef" v-bind="crudBinding">
      <template #actionbar-right>
        <a-alert
          class="ml-1"
          message="非专业人士,请勿随便乱动"
          style="margin-top: 10px"
          type="info"
        />
      </template>
      <template #form_predicates="scope">
        <div
          v-for="(scopeItem, scopeIndex) in scope.form.predicates"
          :key="scopeIndex"
        >
          <a-divider>
            {{ scopeItem.name }}
            <a-button
              v-show="scope.mode !== 'view'"
              @click="
                removePredicate(scopeItem, scopeIndex, scope.form, scope.key)
              "
            >
              <DeleteOutlined />
            </a-button>
          </a-divider>
          <a-tag
            v-for="(tag, index) in scopeItem.args"
            :key="index"
            :closable="scope.mode !== 'view'"
            color="success"
            @close="removePredicateTag(scopeItem, tag)"
          >
            {{ tag }}
          </a-tag>
          <a-input
            v-if="scopeItem.inputVisible"
            v-model:value="scopeItem.inputValue"
            :style="{ width: '100px' }"
            size="small"
            type="text"
            @blur="handleInputConfirm(scopeItem)"
            @keyup.enter="handleInputConfirm(scopeItem)"
          />
          <a-tag
            v-else
            v-show="scope.mode !== 'view'"
            color="#2db7f5"
            style="margin-bottom: 15px; margin-left: 10px"
            @click="showInput(scopeItem)"
          >
            <PlusSquareOutlined />
            新建{{ scopeItem.predicate }}
          </a-tag>
        </div>
        <a-dropdown>
          <a-button
            v-show="scope.mode !== 'view'"
            style="width: 100%"
            type="dashed"
            @mouseover="processPredicates(scope)"
          >
            添加路由条件
            <DownOutlined />
          </a-button>
          <template #overlay>
            <a-menu style="margin-top: 12px">
              <!-- 取出差集 -->
              <a-menu-item
                v-for="predicate in router.predicates"
                :key="predicate"
                @click="handlePredicateChange(predicate, scope.form, scope.key)"
              >
                {{ predicate }}
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </template>

      <template #form_filters="scope">
        <div
          v-for="(scopeItem, scopeIndex) in scope.form.filters"
          :key="scopeIndex"
        >
          <!--          {{scopeItem}}-->
          <a-divider>
            {{ scopeItem.name }}
            <a-button
              v-show="scope.mode !== 'view'"
              @click="
                removeFilter(scopeItem, scopeIndex, scope.form, scope.key)
              "
            >
              <DeleteOutlined />
            </a-button>
          </a-divider>
          <div
            v-for="(tag, index) in scopeItem.args"
            :key="tag.key"
            style="margin-bottom: 10px"
          >
            <a-form-item>
              <a-input
                v-model:value="tag.key"
                :disabled="scope.mode === 'view'"
                placeholder="参数键"
                style="width: 45%; margin-right: 8px"
              />
              <a-input
                v-model:value="tag.value"
                :disabled="scope.mode === 'view'"
                placeholder="参数值"
                style="width: 40%; margin-right: 8px"
              />
              <DeleteOutlined
                v-show="scope.mode !== 'view'"
                @click="removeFilterParams(scopeItem, index)"
              />
            </a-form-item>
          </div>
          <a-button
            v-show="scope.mode !== 'view'"
            size="small"
            style="width: 30%; margin-left: 28%"
            type="dashed"
            @click="addFilterParams(scopeItem)"
          >
            <PlusSquareOutlined />
            添加参数
          </a-button>
        </div>
        <a-dropdown>
          <a-button
            v-show="scope.mode !== 'view'"
            style="width: 100%"
            type="dashed"
            @mouseover="processFilters(scope)"
          >
            添加过滤器
            <DownOutlined />
          </a-button>
          <template #overlay>
            <a-menu style="margin-top: 12px">
              <a-menu-item
                v-for="filter in router.filters"
                :key="filter.name"
                @click="handleFilterChange(filter, scope.form, scope.key)"
              >
                {{ filter.title }}
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </template>
    </fs-crud>
  </fs-page>
</template>
