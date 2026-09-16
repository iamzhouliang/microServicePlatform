import type {
  AddReq,
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
  DelReq,
  EditReq,
  UserPageQuery,
  UserPageRes,
} from '@fast-crud/fast-crud';

import { dict } from '@fast-crud/fast-crud';
import dayjs from 'dayjs';

import { hiddenIdColumn } from '#/plugin/fast-crud/shared';

import * as api from './api';

export default function crud(
  props: CreateCrudOptionsProps,
): CreateCrudOptionsRet {
  return {
    crudOptions: {
      request: {
        pageRequest: async (query: UserPageQuery): Promise<UserPageRes> => {
          query.type = 'button';
          return (await api.GetResourceList(query)) as UserPageRes;
        },
        addRequest: async ({ form }: AddReq) => await api.AddObj(form),
        editRequest: async ({ form }: EditReq) => await api.UpdateObj(form),
        delRequest: async ({ row }: DelReq) => await api.DelObj(row.id),
      },
      toolbar: { show: false },
      container: { is: 'fs-layout-default' },
      actionbar: { buttons: { add: { show: false } } },
      table: { size: 'small', scroll: { fixed: true } },
      search: { show: false },
      buttons: { show: false },
      rowHandle: {
        align: 'center',
        width: 160,
        buttons: {
          view: { show: false },
          edit: { dropdown: false },
          remove: { dropdown: false },
        },
      },
      columns: {
        id: hiddenIdColumn,
        parentId: {
          title: '父ID',
          type: 'text',
          column: { show: false },
          form: {
            show: false,
            component: { disabled: true },
            rules: [{ required: true, message: '请选择菜单后操作' }],
          },
        },
        type: {
          title: '类型',
          type: 'dict-select',
          column: { show: false },
          dict: dict({
            data: [
              { value: 'menu', label: '菜单' },
              { value: 'button', label: '按钮' },
            ],
          }),
          form: { value: 'button', show: false, component: { disabled: true } },
        },
        title: {
          title: '标题',
          type: 'text',
          column: { width: 130, ellipsis: true },
          form: {
            rules: [{ required: true, message: '请填写按钮标题' }],
          },
        },
        permission: {
          title: '编码',
          type: 'text',
          column: { width: 200, ellipsis: true },
          form: {
            component: { placeholder: '资源权限编码' },
            rules: [{ required: true, message: '请填写资源权限编码' }],
            helper: '如（sys:user:add sys:user:edit sys:user:remove）',
          },
        },
        sequence: {
          title: '排序',
          type: 'number',
          column: { width: 80 },
          form: {
            value: 0,
            component: { min: 0, placeholder: '排序值' },
          },
        },
        description: {
          title: '描述',
          column: { width: 100, show: false, ellipsis: true },
          type: ['textarea'],
          form: { col: { span: 24 } },
        },
        createTime: {
          title: '创建时间',
          type: 'datetime',
          form: { show: false },
          column: { width: 180, sorter: true },
          valueBuilder({ value, row, key }: any): void {
            if (value) {
              row[key] = dayjs(value);
            }
          },
        },
      },
    },
  };
}
