<script setup lang="ts">
import type {
  DataPermissionResp,
  DimensionConfig,
  DimensionDataItem,
} from './api';

import type { DataSelectorConfig } from '#/components/data-selector';

/**
 * 数据权限配置抽屉组件
 * 使用 fast-crud ui 适配层方式
 */
import { computed, reactive, ref } from 'vue';

import { useUi } from '@fast-crud/ui-interface';
import { Button, Empty, message, Select, Spin, Tag } from 'ant-design-vue';

import { DataSelectorModal } from '#/components/data-selector';

import {
  dimensionConfigs,
  getRoleDataScopes,
  saveRoleDataScopes,
  ScopeTypeEnum,
  scopeTypeOptions,
} from './api';

const { ui } = useUi();

const emit = defineEmits<{
  (e: 'saved'): void;
}>();

// 抽屉状态
const state = reactive({
  roleId: '' as string,
  roleName: '' as string,
  drawerShow: false,
});
const saving = ref(false);

// 状态
const loading = ref(false);
const dimensions = ref<DimensionConfig[]>([]);
const dimensionDataMap = ref<Record<string, DimensionDataItem[]>>({});
const scopeConfigs = ref<
  Record<string, { dataIds: (number | string)[]; scopeType: number }>
>({});
// 校验状态：记录哪些维度的自定义数据未选择
const validationErrors = ref<Record<string, boolean>>({});

// 选择器弹窗状态
const selectorModalOpen = ref(false);
const currentDimensionCode = ref<string>('');
const currentSelectorConfig = ref<DataSelectorConfig>({
  mode: 'flat',
  title: '选择数据',
  multiple: true,
  showSearch: true,
  pageSize: 20,
});
const currentSelectedIds = ref<(number | string)[]>([]);

// 树形维度的权限范围选项（全部5个）
const treeScopeOptions = computed(() => scopeTypeOptions);

// 扁平维度的权限范围选项（仅全部、自定义、个人）
const flatScopeOptions = computed(() =>
  scopeTypeOptions.filter((opt) =>
    (
      [ScopeTypeEnum.ALL, ScopeTypeEnum.CUSTOM, ScopeTypeEnum.SELF] as number[]
    ).includes(opt.value),
  ),
);

// 根据维度类型获取可用的权限范围选项
function getScopeOptionsForDimension(dim: DimensionConfig) {
  return dim.treeAble ? treeScopeOptions.value : flatScopeOptions.value;
}

/**
 * 打开抽屉
 */
async function open(row: { roleId: string; roleName?: string }) {
  state.roleId = row.roleId;
  state.roleName = row.roleName || '';
  state.drawerShow = true;

  // 重置状态
  for (const code of Object.keys(scopeConfigs.value)) {
    scopeConfigs.value[code] = { scopeType: ScopeTypeEnum.SELF, dataIds: [] };
  }
  validationErrors.value = {};
  dimensionDataMap.value = {};

  // 初始化维度配置
  await initDimensions();
}

// 初始化维度配置
async function initDimensions() {
  loading.value = true;
  try {
    // 使用写死的维度配置
    dimensions.value = dimensionConfigs;
    // 初始化每个维度的配置，默认为「个人」
    for (const dim of dimensions.value) {
      if (!scopeConfigs.value[dim.code]) {
        scopeConfigs.value[dim.code] = {
          scopeType: ScopeTypeEnum.SELF,
          dataIds: [],
        };
      }
    }
    // 加载已保存的数据权限配置
    if (state.roleId) {
      await loadSavedScopes();
    }
  } finally {
    loading.value = false;
  }
}

// 加载已保存的数据权限配置（编辑时回显）
async function loadSavedScopes() {
  if (!state.roleId) return;
  const savedScopes = await getRoleDataScopes(state.roleId);
  for (const scope of savedScopes) {
    // 将 dataIds 转换为数字（后端可能返回字符串）
    const dataIds = (scope.dataIds || []).map((id) =>
      typeof id === 'string' ? Number(id) : id,
    );
    scopeConfigs.value[scope.dataType] = {
      scopeType: scope.scopeType,
      dataIds,
    };
    // 如果是自定义，预加载数据
    if (scope.scopeType === ScopeTypeEnum.CUSTOM) {
      await loadDimensionData(scope.dataType);
    }
  }
}

// 加载维度数据
async function loadDimensionData(dimensionCode: string) {
  if (dimensionDataMap.value[dimensionCode]) return;
  // 从维度配置中找到对应的 loadData 函数
  const dim = dimensions.value.find((d) => d.code === dimensionCode);
  if (dim?.loadData) {
    const data = await dim.loadData();
    dimensionDataMap.value[dimensionCode] = data;
  }
}

// 处理权限范围变更
async function handleScopeTypeChange(dimensionCode: string, scopeType: number) {
  const config = scopeConfigs.value[dimensionCode];
  if (!config) return;
  config.scopeType = scopeType;
  // 如果选择自定义，加载该维度的数据
  if (scopeType === ScopeTypeEnum.CUSTOM) {
    await loadDimensionData(dimensionCode);
  }
  // 如果不是自定义，清空已选数据
  if (scopeType !== ScopeTypeEnum.CUSTOM) {
    config.dataIds = [];
  }
}

// 处理自定义数据选择变更
function handleDataIdsChange(
  dimensionCode: string,
  dataIds: (number | string)[],
) {
  const config = scopeConfigs.value[dimensionCode];
  if (!config) return;
  config.dataIds = dataIds;
  // 清除校验错误
  if (dataIds.length > 0) {
    validationErrors.value[dimensionCode] = false;
  }
}

// 校验配置：自定义范围必须选择至少一条数据
function validate(): boolean {
  let isValid = true;
  for (const dim of dimensions.value) {
    const config = scopeConfigs.value[dim.code];
    if (
      config?.scopeType === ScopeTypeEnum.CUSTOM &&
      (!config.dataIds || config.dataIds.length === 0)
    ) {
      validationErrors.value[dim.code] = true;
      isValid = false;
    } else {
      validationErrors.value[dim.code] = false;
    }
  }
  return isValid;
}

// 检查某个维度是否有校验错误
function hasError(dimensionCode: string): boolean {
  return validationErrors.value[dimensionCode] === true;
}

// 获取权限范围标签颜色
function getScopeTypeColor(scopeType: number): string {
  const option = scopeTypeOptions.find((opt) => opt.value === scopeType);
  return option?.color || 'default';
}

// 打开选择器弹窗
async function openSelectorModal(dim: DimensionConfig) {
  currentDimensionCode.value = dim.code;
  // 加载数据
  await loadDimensionData(dim.code);
  // 配置选择器
  currentSelectorConfig.value = {
    mode: dim.treeAble ? 'tree' : 'flat',
    title: `选择${dim.name}`,
    multiple: true,
    showSearch: true,
    pageSize: 20,
    checkStrictly: true,
  };
  // 设置已选中的值
  currentSelectedIds.value = scopeConfigs.value[dim.code]?.dataIds || [];
  selectorModalOpen.value = true;
}

// 处理选择器确认
function handleSelectorConfirm(ids: (number | string)[]) {
  if (currentDimensionCode.value) {
    handleDataIdsChange(currentDimensionCode.value, ids);
  }
}

// 获取已选数据的名称列表
function getSelectedNames(dimensionCode: string): string[] {
  const data = dimensionDataMap.value[dimensionCode] || [];
  const ids = scopeConfigs.value[dimensionCode]?.dataIds || [];
  const names: string[] = [];

  const findNames = (items: DimensionDataItem[]) => {
    for (const item of items) {
      if (ids.includes(item.id)) {
        names.push(item.name);
      }
      if (item.children) {
        findNames(item.children);
      }
    }
  };
  findNames(data);
  return names;
}

// 获取配置值
function getValues(): DataPermissionResp[] {
  const result: DataPermissionResp[] = [];
  for (const [code, config] of Object.entries(scopeConfigs.value)) {
    result.push({
      dataType: code,
      scopeType: config.scopeType,
      dataIds: config.dataIds,
    });
  }
  return result;
}

/**
 * 保存
 */
async function handleSave() {
  if (!validate()) {
    message.error('请完善数据权限配置');
    return;
  }
  saving.value = true;
  try {
    const scopes = getValues();
    await saveRoleDataScopes(state.roleId, scopes);
    message.success('数据权限保存成功');
    state.drawerShow = false;
    emit('saved');
  } finally {
    saving.value = false;
  }
}

/**
 * 取消
 */
function handleCancel() {
  state.drawerShow = false;
}

defineExpose({
  open,
});
</script>

<template>
  <component
    :is="ui.drawer.name"
    v-if="state.drawerShow"
    v-model:[ui.drawer.visible]="state.drawerShow"
    :title="`数据权限配置${state.roleName ? ' - ' + state.roleName : ''}`"
    width="50%"
    class="data-scope-drawer"
  >
    <div class="data-scope-config">
      <a-alert
        message="注：演示多维度数据权限使用和交互"
        type="info"
        show-icon
        class="mb-4"
      />
      <Spin :spinning="loading">
        <Empty v-if="dimensions.length === 0" description="暂无维度配置" />

        <div v-else class="dimension-list">
          <div v-for="dim in dimensions" :key="dim.code" class="dimension-item">
            <div class="dimension-header">
              <span class="dimension-name">{{ dim.name }}</span>
              <Tag
                :color="
                  getScopeTypeColor(
                    scopeConfigs[dim.code]?.scopeType || ScopeTypeEnum.SELF,
                  )
                "
                class="scope-tag"
              >
                {{
                  scopeTypeOptions.find(
                    (opt) =>
                      opt.value ===
                      (scopeConfigs[dim.code]?.scopeType || ScopeTypeEnum.SELF),
                  )?.label || '个人'
                }}
              </Tag>
            </div>

            <div class="dimension-content">
              <div class="scope-select">
                <span class="field-label">权限范围</span>
                <Select
                  :options="getScopeOptionsForDimension(dim)"
                  :value="scopeConfigs[dim.code]?.scopeType || ScopeTypeEnum.SELF"
                  placeholder="请选择权限范围"
                  size="small"
                  style="width: 160px"
                  @change="
                    (val: any) => handleScopeTypeChange(dim.code, val as number)
                  "
                />
              </div>

              <div
                v-if="scopeConfigs[dim.code]?.scopeType === ScopeTypeEnum.CUSTOM"
                class="data-select"
              >
                <span class="field-label">选择数据</span>
                <div class="selected-preview">
                  <div
                    v-if="(scopeConfigs[dim.code]?.dataIds || []).length > 0"
                    class="selected-tags"
                  >
                    <Tag
                      v-for="name in getSelectedNames(dim.code).slice(0, 3)"
                      :key="name"
                      size="small"
                    >
                      {{ name }}
                    </Tag>
                    <Tag
                      v-if="(scopeConfigs[dim.code]?.dataIds || []).length > 3"
                      size="small"
                    >
                      +{{ (scopeConfigs[dim.code]?.dataIds || []).length - 3 }}
                    </Tag>
                  </div>
                  <span v-else class="no-data">未选择</span>
                </div>
                <Button
                  :class="{ 'has-error': hasError(dim.code) }"
                  size="small"
                  type="primary"
                  ghost
                  @click="openSelectorModal(dim)"
                >
                  选择（{{ (scopeConfigs[dim.code]?.dataIds || []).length }}）
                </Button>
                <span v-if="hasError(dim.code)" class="error-text">
                  请至少选择一条数据
                </span>
              </div>

              <div v-else class="scope-hint">
                <span class="hint-text">
                  {{
                    scopeConfigs[dim.code]?.scopeType === ScopeTypeEnum.ALL
                      ? '可查看该维度全部数据'
                      : scopeConfigs[dim.code]?.scopeType === ScopeTypeEnum.SELF
                        ? '仅可查看本人创建的数据'
                        : scopeConfigs[dim.code]?.scopeType === ScopeTypeEnum.DEPT
                          ? '可查看本部门的数据'
                          : '可查看本部门及子部门的数据'
                  }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </Spin>

      <!-- 数据选择器弹窗 -->
      <DataSelectorModal
        v-model:open="selectorModalOpen"
        :config="currentSelectorConfig"
        :data-source="dimensionDataMap[currentDimensionCode] || []"
        :value="currentSelectedIds"
        @confirm="handleSelectorConfirm"
      />
    </div>
    <template #footer>
      <div class="flex justify-end gap-2">
        <Button @click="handleCancel">取消</Button>
        <Button :loading="saving" type="primary" @click="handleSave">
          保存
        </Button>
      </div>
    </template>
  </component>
</template>

<style scoped>
.data-scope-config {
  width: 100%;
}

.dimension-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dimension-item {
  padding: 12px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  transition: all 0.2s;
}

.dimension-item:hover {
  border-color: #d9d9d9;
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
}

.dimension-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.dimension-name {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
}

.scope-tag {
  margin: 0;
}

.dimension-content {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}

.scope-select {
  display: flex;
  gap: 8px;
  align-items: center;
}

.data-select {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 300px;
}

.field-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #8c8c8c;
}

.scope-hint {
  flex: 1;
}

.hint-text {
  font-size: 13px;
  color: #8c8c8c;
}

.error-text {
  flex-basis: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: #ff4d4f;
}

.has-error {
  border-color: #ff4d4f;
}

.selected-preview {
  flex: 1;
  min-width: 0;
}

.selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.no-data {
  font-size: 13px;
  color: #bfbfbf;
}
</style>
