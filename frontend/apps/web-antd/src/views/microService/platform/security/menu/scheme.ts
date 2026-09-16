import type { VbenFormSchema } from '#/adapter/form';

import { h } from 'vue';

import { useVbenForm } from '#/adapter/form';
import { getAllMenusApi } from '#/api';

import { GetAppList } from './api';

/**
 * 菜单类型选项
 */
export function getMenuTypeOptions() {
  return [
    { label: '目录', value: 'directory', color: 'processing' },
    { label: '菜单', value: 'menu', color: 'success' },
    { label: '内嵌', value: 'iframe', color: 'default' },
    { label: '外链', value: 'link', color: 'warning' },
  ];
}

/**
 * 菜单表单 Schema - 参考 vben5 官方布局
 */
const schema: VbenFormSchema[] = [
  // === 隐藏字段 ===
  {
    fieldName: 'id',
    component: 'Input',
    dependencies: { show: false, triggerFields: ['id'] },
  },
  {
    fieldName: 'clientId',
    component: 'Input',
    dependencies: { show: false, triggerFields: ['clientId'] },
  },
  // === 第一行：所属应用 ===
  {
    fieldName: 'clientIdDisplay',
    component: 'ApiSelect',
    label: '所属应用',
    formItemClass: 'col-span-2',
    componentProps: {
      disabled: true,
      class: 'w-full',
      api: GetAppList,
      labelField: 'clientName',
      valueField: 'clientId',
    },
    dependencies: {
      // 同步 clientId 的值到显示字段
      trigger(values, formApi) {
        if (values?.clientId && !values?.clientIdDisplay) {
          formApi.setValues({ clientIdDisplay: values.clientId });
        }
      },
      triggerFields: ['clientId'],
    },
  },
  // === 第二行：类型 ===
  {
    fieldName: 'type',
    component: 'RadioGroup',
    label: '类型',
    formItemClass: 'col-span-2',
    defaultValue: 'directory',
    dependencies: {
      trigger(values, formApi) {
        if (values?.id === undefined) {
          formApi.setValues({
            component: values.type === 'directory' ? 'BasicLayout' : '',
          });
        }
      },
      triggerFields: ['type'],
    },
    componentProps: {
      optionType: 'button',
      buttonStyle: 'solid',
      options: getMenuTypeOptions(),
    },
    rules: 'required',
  },
  // === 第二行：菜单名称 | 上级菜单 ===
  {
    fieldName: 'title',
    component: 'Input',
    label: '菜单名称',
    componentProps: { placeholder: '请输入' },
    rules: 'required',
  },
  {
    fieldName: 'parentId',
    component: 'ApiTreeSelect',
    label: '上级菜单',
    dependencies: {
      show: (values) => values.type !== 'directory',
      triggerFields: ['type'],
    },
    componentProps: {
      api: getAllMenusApi,
      allowClear: true,
      class: 'w-full',
      placeholder: '请选择',
      fieldNames: { label: 'title', value: 'id', children: 'children' },
      treeDefaultExpandAll: true,
      showSearch: true,
      filterTreeNode: (input: string, node: any) => {
        if (!input) return true;
        return node.title?.toLowerCase().includes(input.toLowerCase());
      },
    },
  },
  // === 第三行：标题 | 路由地址 ===
  {
    fieldName: 'name',
    component: 'Input',
    label: '标题',
    componentProps: { placeholder: '请输入' },
  },
  {
    fieldName: 'path',
    component: 'Input',
    label: '路由地址',
    componentProps: { placeholder: '请输入' },
    rules: 'required',
  },
  // === 第四行：图标 | 激活图标 ===
  {
    fieldName: 'icon',
    component: 'IconPicker',
    label: '图标',
    componentProps: { placeholder: '请选择', prefix: 'carbon' },
    dependencies: {
      // 选择图标后，自动同步到激活图标（如果激活图标为空）
      trigger(values, formApi) {
        if (values?.icon && !values?.activeIcon) {
          formApi.setValues({ activeIcon: values.icon });
        }
      },
      triggerFields: ['icon'],
    },
  },
  {
    fieldName: 'activeIcon',
    component: 'IconPicker',
    label: '激活图标',
    componentProps: { placeholder: '请选择', prefix: 'carbon' },
  },
  // === 第五行：权限标识 | 排序 ===
  {
    fieldName: 'permission',
    component: 'Input',
    label: '权限标识',
    componentProps: { placeholder: '请输入' },
  },
  {
    fieldName: 'sequence',
    component: 'InputNumber',
    label: '排序',
    defaultValue: 0,
    componentProps: { min: 0, placeholder: '排序值', class: 'w-full' },
  },
  // === 第六行：状态 ===
  {
    fieldName: 'status',
    component: 'RadioGroup',
    label: '状态',
    defaultValue: true,
    componentProps: {
      optionType: 'button',
      buttonStyle: 'solid',
      options: [
        { label: '已启用', value: true },
        { label: '已禁用', value: false },
      ],
    },
  },
  // === 第六行：徽标类型 | 徽章内容 ===
  {
    fieldName: 'badgeType',
    component: 'Select',
    label: '徽标类型',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      placeholder: '请选择',
      options: [
        { label: '圆点', value: 'dot' },
        { label: '文本', value: 'text' },
      ],
    },
  },
  {
    fieldName: 'badge',
    component: 'Input',
    label: '徽章内容',
    componentProps: { placeholder: '请输入' },
  },
  // === 第七行：徽标样式 ===
  {
    fieldName: 'badgeVariants',
    component: 'Select',
    label: '徽标样式',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      placeholder: '请选择',
      options: [
        { label: 'default', value: 'default' },
        { label: 'destructive', value: 'destructive' },
        { label: 'primary', value: 'primary' },
        { label: 'success', value: 'success' },
        { label: 'warning', value: 'warning' },
      ],
    },
  },
  // === 组件路径（仅菜单类型） ===
  {
    fieldName: 'component',
    component: 'Textarea',
    label: '组件路径',
    formItemClass: 'col-span-2 md:col-span-2',
    componentProps: { placeholder: '请输入', rows: 2 },
    dependencies: {
      show: (values) => values.type === 'menu',
      triggerFields: ['type'],
    },
  },
  // === 外链地址（外链/内嵌类型） ===
  {
    fieldName: 'url',
    component: 'Input',
    label: '外链地址',
    componentProps: { placeholder: '请输入' },
    dependencies: {
      show: (values) => ['iframe', 'link'].includes(values.type),
      triggerFields: ['type'],
    },
  },
  // === 分隔线 ===
  {
    component: 'Divider',
    fieldName: 'divider',
    formItemClass: 'col-span-2 md:col-span-2',
    hideLabel: true,
    renderComponentContent: () => ({
      default: () => h('span', {}, '其它设置'),
    }),
  },
  // === 描述 ===
  {
    fieldName: 'description',
    component: 'Textarea',
    label: '描述',
    formItemClass: 'col-span-2 md:col-span-2',
    componentProps: { placeholder: '请输入描述', rows: 2, maxlength: 200 },
  },
  // === 高级设置：Checkbox 布局 ===
  {
    fieldName: 'shared',
    component: 'Checkbox',
    renderComponentContent: () => ({
      default: () => '公共菜单（所有应用可见）',
    }),
  },
  {
    fieldName: 'hideInMenu',
    component: 'Checkbox',
    renderComponentContent: () => ({
      default: () => '隐藏菜单',
    }),
  },
  {
    fieldName: 'hideChildrenInMenu',
    component: 'Checkbox',
    dependencies: {
      show: (values) => ['directory', 'menu'].includes(values.type),
      triggerFields: ['type'],
    },
    renderComponentContent: () => ({
      default: () => '隐藏子菜单',
    }),
  },
  {
    fieldName: 'hideInBreadcrumb',
    component: 'Checkbox',
    renderComponentContent: () => ({
      default: () => '在面包屑中隐藏',
    }),
  },
  {
    fieldName: 'hideInTab',
    component: 'Checkbox',
    renderComponentContent: () => ({
      default: () => '在标签栏中隐藏',
    }),
  },
  {
    fieldName: 'keepAlive',
    component: 'Checkbox',
    dependencies: {
      show: (values) => values.type === 'menu',
      triggerFields: ['type'],
    },
    renderComponentContent: () => ({
      default: () => '页面缓存',
    }),
  },
  {
    fieldName: 'affixTab',
    component: 'Checkbox',
    dependencies: {
      show: (values) => ['iframe', 'menu'].includes(values.type),
      triggerFields: ['type'],
    },
    renderComponentContent: () => ({
      default: () => '固定标签页',
    }),
  },
];

/**
 * 菜单表单配置
 */
export function menuForm(handleSubmit: any) {
  return useVbenForm({
    commonConfig: {
      colon: true,
      formItemClass: 'col-span-2 md:col-span-1',
    },
    handleSubmit,
    showDefaultActions: false,
    wrapperClass: 'grid-cols-2 gap-x-4',
    schema,
  });
}
