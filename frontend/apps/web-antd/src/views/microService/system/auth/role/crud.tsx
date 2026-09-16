import type {
  AddReq,
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
  DelReq,
  EditReq,
  InfoReq,
  UserPageQuery,
} from '@fast-crud/fast-crud';

import { useAccess } from '@vben/access';

import { compute } from '@fast-crud/fast-crud';

import {
  booleanDict,
  createTimeReadonlyColumn,
  hiddenIdColumn,
  statusDict,
} from '#/plugin/fast-crud/shared';

import * as api from './api';

export default function crud(
  props: CreateCrudOptionsProps,
): CreateCrudOptionsRet {
  const { assign } = props.context;
  const { hasPermission } = useAccess();
  return {
    crudOptions: {
      request: {
        pageRequest: async (query: UserPageQuery) => await api.GetList(query),
        infoRequest: async ({ mode, row }: InfoReq) => {
          if (mode !== 'add') {
            return await api.GetObj(row.id);
          }
          return row;
        },
        addRequest: async ({ form }: AddReq) => await api.AddObj(form),
        editRequest: async ({ form }: EditReq) => await api.UpdateObj(form),
        delRequest: async ({ row }: DelReq) => await api.DelObj(row.id),
      },
      table: { size: 'small', scroll: { fixed: true } },
      rowHandle: {
        show: true,
        width: 300,
        align: 'right',
        buttons: {
          view: { show: false },
          edit: {
            show: compute(({ row }) => {
              return hasPermission('sys:role:edit') && !row.readonly;
            }),
          },
          remove: {
            show: compute(({ row }) => {
              return hasPermission('sys:role:del') && !row.readonly;
            }),
          },
          distribution: {
            text: '分配用户',
            size: 'small',
            type: 'link',
            order: 4,
            show: hasPermission('sys:role:assign-users'),
            async click({ row }: any) {
              await assign.userModal(row.id);
            },
          },
          resource: {
            text: '分配权限',
            type: 'link',
            size: 'small',
            order: 5,
            show: hasPermission('sys:role:assign-resource'),
            async click({ row }: any) {
              await assign.resourceModal(row.id);
            },
          },
          dataScope: {
            text: '数据权限',
            type: 'link',
            size: 'small',
            order: 6,
            // show: hasPermission('sys:role:data-scope'),
            async click({ row }: any) {
              await assign.dataScopeModal(row.id, row.name);
            },
          },
        },
      },
      columns: {
        id: hiddenIdColumn,
        code: {
          title: '编码',
          type: 'text',
          column: { width: 180 },
          form: {
            rules: [
              { required: true, message: '请输入编码' },
              { min: 2, max: 30, message: '长度在 2 到 30 个字符' },
            ],
          },
        },
        name: {
          title: '名称',
          type: 'text',
          column: { width: 180 },
          search: { show: true },
          form: {
            rules: [
              { required: true, message: '请输入名称' },
              { min: 2, max: 30, message: '长度在 2 到 30 个字符' },
            ],
          },
        },
        readonly: {
          title: '内置角色',
          type: 'dict-radio',
          column: { width: 90, align: 'center' },
          form: { show: false },
          dict: booleanDict(),
        },
        status: {
          title: '状态',
          type: 'dict-radio',
          search: { show: true },
          column: { width: 100, align: 'center' },
          addForm: { value: true },
          dict: statusDict(),
        },
        description: {
          title: '描述',
          search: { show: false },
          column: { width: 230, ellipsis: true },
          type: ['textarea'],
          form: {
            col: { span: 24 },
          },
        },
        createTime: createTimeReadonlyColumn,
      },
    },
  };
}
