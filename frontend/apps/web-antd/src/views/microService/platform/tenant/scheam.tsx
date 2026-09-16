import { compute, dict, useColumns } from '@fast-crud/fast-crud';

import { defHttp } from '#/api/request';
import createCrudOptionsText from '#/views/microService/platform/db/crud';

import { saveTenantDbBinding } from './api';

const { buildFormOptions } = useColumns();

/** 隔离策略配置 */
const ISOLATION_STRATEGIES = [
  {
    value: 'DATASOURCE',
    label: '独立数据库',
    color: 'success',
    description: '每个租户使用独立的数据库，数据完全隔离',
  },
  {
    value: 'SCHEMA',
    label: 'Schema隔离',
    color: 'processing',
    description: '同一数据库内使用不同Schema隔离租户数据',
  },
  {
    value: 'COLUMN',
    label: '字段隔离',
    color: 'warning',
    description: '同表通过租户ID字段区分数据，适合小租户场景',
  },
];

// 使用crudOptions结构来构建自定义表单配置
const tenantSettingOptions = {
  columns: {
    siteUrl: {
      title: '站点',
      type: ['textarea'],
      column: { ellipsis: true, show: false },
      form: {
        col: { span: 24 },
        rules: [
          { required: true, message: '请输入租户站点' },
          { min: 2, max: 100, message: '长度在 2 到 100 个字符' },
        ],
      },
    },
    siteTitle: {
      title: '标题',
      type: ['text'],
      column: { ellipsis: true, show: false },
    },
    siteSubTitle: {
      title: '子标题',
      type: ['textarea'],
      form: {
        col: {
          span: 24,
        },
      },
    },
    siteLogo: {
      title: 'LOGO',
      type: 'cropper-uploader',
      style: { height: 70 },
      column: { width: 70, align: 'center', show: true },
      form: {
        component: {
          uploader: {
            type: 'form',
            buildUrl(res: any) {
              return res.url;
            },
          },
        },
      },
    },
    dbId: {
      title: 'DB配置',
      type: 'table-select',
      dict: dict({
        value: 'id',
        label: 'name',
        getNodesByValues: async (values: any[]) => {
          return defHttp.get('/iam/db-instances/active', { params: values });
        },
      }),
      form: {
        component: {
          crossPage: true,
          valuesFormat: {
            labelFormatter: (item: any) => {
              return `${item.id}.${item.name}`;
            },
          },
          select: { placeholder: '点击选择' },
          createCrudOptions: createCrudOptionsText,
          crudOptionsOverride: {
            toolbar: { show: false },
            actionbar: { buttons: { add: { show: false } } },
            rowHandle: { show: false },
          },
          on: {
            selectedChange({ form, $event }: any) {
              form.dbConfig = `${$event[0].host},${$event[0].username},${
                $event[0].password
              }`;
            },
          },
        },
        helper: '选择数据源后,租户数据将会写入到指定的库中',
      },
    },
    dbConfig: {
      title: 'DB信息',
      type: 'textarea',
      form: {
        col: { span: 24 },
        component: { disabled: true },
      },
    },
  },
  form: {
    group: {
      groupType: 'collapse',
      accordion: false,
      groups: {
        'site-info': {
          tab: '站点配置',
          header: '站点配置',
          columns: ['siteUrl', 'siteTitle', 'siteSubTitle', 'siteLogo'],
        },
        'db-config': {
          tab: 'DB配置',
          header: 'DB配置',
          columns: ['dbId', 'dbConfig'],
        },
      },
    },
    wrapper: { title: '租户设置' },
    async doSubmit({ form }: any) {
      return await defHttp.put(`/iam/tenants/${form.tenantId}/setting`, form, {
        fetchOptions: { mode: 'full' },
      });
    },
    afterSubmit(ctx: any) {
      if(!ctx.res){
        return true;
      }
      if (!ctx.res?.successful) {
        return false;
      }
    },
  },
};
export const tenantSettingFormOptions = buildFormOptions(tenantSettingOptions);

/** 租户数据源绑定表单配置 */
const tenantDbBindingOptions = {
  columns: {
    tenantName: {
      title: '租户',
      type: 'text',
      form: {
        col: { span: 24 },
        component: { disabled: true },
      },
    },
    strategy: {
      title: '隔离策略',
      type: 'dict-radio',
      dict: dict({ data: ISOLATION_STRATEGIES }),
      addForm:{value: 'COLUMN'},
      form: {
        col: { span: 24 },
        rules: [{ required: true, message: '请选择隔离策略' }],
        helper: compute(({ form }) => {
          const strategy = ISOLATION_STRATEGIES.find(
            (s) => s.value === form?.strategy,
          );
          return strategy?.description || '请选择一种数据隔离策略';
        }),
        // 切换策略时清空相关字段
        valueChange({ form, value }: any) {
          if (value === 'COLUMN') {
            // 字段隔离不需要数据源和schema
            form.dbInstanceId = null;
            form.schemaName = null;
          } else if (value === 'DATASOURCE') {
            // 独立数据库不需要schema
            form.schemaName = null;
          }
        },
      },
    },
    dbInstanceId: {
      title: '数据源',
      type: 'table-select',
      dict: dict({
        value: 'id',
        label: 'name',
        getNodesByValues: async (values: any[]) => {
          if (!values || values.length === 0) return [];
          return defHttp.get('/iam/db-instances/active', { params: values });
        },
      }),
      form: {
        // DATASOURCE 和 SCHEMA 策略需要选择数据源
        show: compute(({ form }) => {
          return form?.strategy === 'DATASOURCE' || form?.strategy === 'SCHEMA';
        }),
        rules: [{ required: true, message: '请选择数据源' }],
        component: {
          crossPage: true,
          valuesFormat: {
            labelFormatter: (item: any) => `${item.name} (${item.dbType})`,
          },
          select: { placeholder: '点击选择数据源' },
          createCrudOptions: createCrudOptionsText,
          crudOptionsOverride: {
            toolbar: { show: false },
            actionbar: { buttons: { add: { show: false } } },
            rowHandle: { show: false },
          },
        },
        helper: compute(({ form }) => {
          if (form?.strategy === 'DATASOURCE') {
            return '选择租户独立使用的数据库实例';
          }
          return '选择租户数据存储的目标数据源';
        }),
      },
    },
    schemaName: {
      title: 'Schema名称',
      type: 'text',
      form: {
        // 仅 SCHEMA 策略需要填写
        show: compute(({ form }) => form?.strategy === 'SCHEMA'),
        rules: [
          { required: true, message: '请输入Schema名称' },
          {
            pattern: /^[a-z_]\w*$/i,
            message: 'Schema名称只能包含字母、数字和下划线',
          },
          { max: 64, message: 'Schema名称不能超过64个字符' },
        ],
        helper: '运行时使用的Schema名称，建议格式：tenant_{租户编码}',
      },
    },
    isPrimary: {
      title: '主数据源',
      type: 'dict-switch',
      dict: dict({
        data: [
          { value: true, label: '是' },
          { value: false, label: '否' },
        ],
      }),
      form: {
        value: true,
        // 字段隔离模式下隐藏此选项（因为共用数据源）
        show: compute(({ form }) => form?.strategy !== 'COLUMN'),
        helper: '是否作为租户的主数据源，每个租户只能有一个主数据源',
      },
    },
  },
  form: {
    labelWidth: '100px',
    wrapper: { title: '数据源绑定' },
    async doSubmit({ form }: any) {
      // 字段隔离模式下设置默认值
      if (form.strategy === 'COLUMN') {
        form.isPrimary = true;
      }
      return await saveTenantDbBinding(form);
    },
    afterSubmit(ctx: any) {
      if(!ctx.res){
        return true;
      }
      if (!ctx.res?.successful) {
        return false;
      }
    },
  },
};

export const tenantDbBindingFormOptions = buildFormOptions(
  tenantDbBindingOptions,
);
