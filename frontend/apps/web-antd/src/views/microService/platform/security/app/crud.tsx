import type {
  AddReq,
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
  DelReq,
  EditReq,
  ValueBuilderContext,
} from '@fast-crud/fast-crud';

import { dict } from '@fast-crud/fast-crud';
import dayjs from 'dayjs';

import { defHttp } from '#/api/request';
import { hiddenIdColumn } from '#/plugin/fast-crud/shared';

export default function crud(
  _props: CreateCrudOptionsProps,
): CreateCrudOptionsRet {
  return {
    crudOptions: {
      request: {
        pageRequest: async (query: any) =>
          await defHttp.get(`/iam/registered-client/page`, { params: query }),
        addRequest: async ({ form }: AddReq) => {
          form.tokenSettings = {
            accessTokenTimeToLive: form.accessTokenTimeToLive,
            refreshTokenTimeToLive: form.refreshTokenTimeToLive,
          };
          await defHttp.post(`/iam/registered-client`, form);
        },
        editRequest: async ({ form }: EditReq) => {
          form.tokenSettings = {
            accessTokenTimeToLive: form.accessTokenTimeToLive,
            refreshTokenTimeToLive: form.refreshTokenTimeToLive,
          };
          await defHttp.put(`/iam/registered-client/${form.id}`, form);
        },
        delRequest: async ({ row }: DelReq) =>
          await defHttp.delete(`/iam/registered-client/${row.id}`),
      },
      table: {
        rowKey: 'clientId',
      },
      columns: {
        id: hiddenIdColumn,
        clientName: {
          title: '客户名称',
          type: 'text',
          column: { ellipsis: true, width: 180 },
          search: { show: true },
          form: {
            rules: [{ required: true, message: '客户名称不能为空' }],
            col: { span: 12 },
          },
        },
        clientId: {
          title: '客户标识',
          type: 'text',
          search: { show: true },
          column: { ellipsis: true, width: 140 },
          editForm: { component: { disabled: true } },
          form: {
            rules: [{ required: true, message: 'clientId 不能为空' }],
            col: { span: 12 },
            helper: '唯一标识，创建后不可修改',
          },
        },
        clientSecret: {
          title: '客户秘钥',
          type: 'password',
          column: { width: 140, show: false },
          editForm: {
            show: false,
          },
          form: {
            rules: [{ required: true, message: 'clientSecret 不能为空' }],
            col: { span: 12 },
            helper: '填写后将以密文存储，编辑时不显示',
          },
        },
        clientIdIssuedAt: {
          title: '生效时间',
          column: { width: 180 },
          type: 'datetime',
          valueBuilder({ value, row, key }: ValueBuilderContext): void {
            if (value !== null) {
              row[key] = dayjs(value);
            }
          },
          valueResolve({ value, row, key }: ValueBuilderContext): void {
            if (value !== null) {
              row[key] = dayjs(value).unix();
            }
          },
          form: {
            col: { span: 12 },
            helper: '不填则默认当前时间',
          },
        },
        clientSecretExpiresAt: {
          title: '过期时间',
          column: {
            width: 180,
            formatter({ value }: { value: any }) {
              if (!value) return '永久有效';
              return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
            },
          },
          type: 'datetime',
          valueBuilder({ value, row, key }: ValueBuilderContext): void {
            if (value !== null && value !== undefined) {
              row[key] = dayjs(value);
            }
          },
          valueResolve({ value, row, key }: ValueBuilderContext): void {
            if (value !== null && value !== undefined) {
              row[key] = dayjs(value).unix();
            }
          },
          form: {
            col: { span: 12 },
            helper: '不填则永不失效',
          },
        },
        grantTypes: {
          title: '登录方式',
          type: 'dict-select',
          form: {
            rules: [{ required: true, message: '请选择支持的登录方式' }],
            component: { mode: 'multiple' },
            col: { span: 12 },
          },
          dict: dict({
            data: [
              { value: 'password', label: '账号密码' },
              { value: 'sms', label: '短信验证码' },
              { value: 'email', label: '邮箱验证码' },
              { value: 'vc_code', label: '图形验证码' },
            ],
          }),
          column: { width: 200, component: { color: 'auto' } },
        },
        status: {
          title: '状态',
          type: 'dict-radio',
          column: { show: true, width: 80 },
          search: { show: true },
          dict: dict({
            data: [
              { value: 1, label: '启用', color: 'success' },
              { value: 0, label: '禁用', color: 'error' },
            ],
          }),
          addForm: { value: 1 },
          form: {
            col: { span: 12 },
          },
          valueBuilder({ value, row, key }: ValueBuilderContext): void {
            if (value !== null) {
              row[key] = value === true ? 1 : 0;
            }
          },
        },
        accessTokenTimeToLive: {
          title: 'AT有效期',
          type: 'number',
          column: { ellipsis: true, width: 120 },
          addForm: {
            value: 120,
          },
          form: {
            col: { span: 12 },
            component: {
              addonAfter: '分钟',
              min: 1,
              max: 43_200,
            },
            rules: [{ required: true, message: 'Access Token有效期不能为空' }],
            helper: 'Access Token有效期，默认120分钟',
          },
        },
        refreshTokenTimeToLive: {
          title: 'RT有效期',
          type: 'number',
          column: { ellipsis: true, width: 120, show: false },
          addForm: {
            value: 4320,
          },
          form: {
            col: { span: 12 },
            component: {
              addonAfter: '分钟',
              min: 1,
              max: 525_600,
            },
            rules: [{ required: true, message: 'Refresh Token有效期不能为空' }],
            helper: 'Refresh Token有效期，默认4320分钟(3天)',
          },
        },
        redirectUris: {
          title: '回调地址',
          type: 'textarea',
          column: { ellipsis: true, show: false },
          form: {
            rules: [{ required: true, message: '请填写回调地址' }],
            col: {
              span: 24,
            },
          },
        },
      },
    },
  };
}
