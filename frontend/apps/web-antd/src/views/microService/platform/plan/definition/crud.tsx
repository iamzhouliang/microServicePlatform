import type {
  AddReq,
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
  DelReq,
  EditReq,
  UserPageQuery,
} from '@fast-crud/fast-crud';

import {
  createTimeReadonlyColumn,
  hiddenIdColumn,
  statusDict,
} from '#/plugin/fast-crud/shared';

import * as api from './api';

export default function crud(
  _props: CreateCrudOptionsProps,
): CreateCrudOptionsRet {
  return {
    crudOptions: {
      table: {},
      request: {
        pageRequest: async (query: UserPageQuery) => await api.GetList(query),
        infoRequest: async ({ mode, row }) => {
          if (mode !== 'add') {
            return await api.GetObj(row.id);
          }
          return row;
        },
        addRequest: async ({ form }: AddReq) => await api.AddObj(form),
        editRequest: async ({ form }: EditReq) => await api.UpdateObj(form),
        delRequest: async ({ row }: DelReq) => await api.DelObj(row.id),
      },
      toolbar: {},
      rowHandle: { width: 180 },
      columns: {
        id: hiddenIdColumn,
        code: {
          title: '套餐编码',
          type: 'text',
          addForm: { show: false },
          editForm: { show: false },
          column: { width: 150 },
          search: { show: true },
        },
        name: {
          title: '套餐名称',
          type: 'text',
          column: { width: 200 },
          search: { show: true },
          form: {
            rules: [{ required: true, message: '名称不能为空' }],
            helper: '请填写符合场景的套餐名称',
          },
        },
        logo: {
          title: 'LOGO',
          type: 'cropper-uploader',
          column: { width: 120, align: 'center' },
          form: {
            rules: [{ required: false, message: 'LOGO不能为空' }],
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
        status: {
          title: '状态',
          type: 'dict-radio',
          column: { width: 100, align: 'center' },
          search: { show: true },
          dict: statusDict(),
          form: {
            show: false,
          },
        },
        description: {
          title: '套餐描述',
          column: { width: 200, show: true, ellipsis: true },
          type: ['textarea'],
          form: {
            rules: [{ required: true, message: '描述不能为空' }],
            col: {
              span: 24,
            },
          },
        },
        createName: {
          title: '创建人',
          type: 'text',
          addForm: { show: false },
          editForm: { show: false },
          column: { width: 150, ellipsis: true },
        },
        createTime: createTimeReadonlyColumn,
        itemIdList: {
          title: '功能权限',
          type: 'text',
          column: { show: false },
          search: { show: false },
          form: {
            slot: true,
            col: { span: 24 },
          },
          valueBuilder({ row, key }) {
            if (row.itemIdList) {
              row[key] = row.itemIdList;
            }
          },
          valueResolve({ form, key }) {
            form.itemIdList = form[key];
          },
        },
      },
    },
  };
}
