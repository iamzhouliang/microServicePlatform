/**
 * 网关限流 - CRUD 配置
 */
import type { AddReq, DelReq, EditReq } from '@fast-crud/fast-crud';

import { dict } from '@fast-crud/fast-crud';

import {
  booleanDict,
  createTimeColumn,
  datetimeRangeColumn,
  descriptionColumn,
  hiddenIdColumn,
  httpMethodDict,
  statusDict,
} from '../shared/index';
import * as api from './api';

export default function () {
  return {
    crudOptions: {
      request: {
        pageRequest: async (query: any) => api.GetList(query),
        addRequest: async ({ form }: AddReq) => api.AddObj(form),
        editRequest: async ({ form, row }: EditReq) => {
          form.id = row.id;
          return api.UpdateObj(form);
        },
        delRequest: async ({ row }: DelReq) => api.DelObj(row.id),
      },
      columns: {
        id: hiddenIdColumn,
        range: {
          title: '范围',
          type: 'dict-select',
          search: { show: true },
          column: { width: 100 },
          dict: dict({
            data: [
              { label: '全局', value: 0, color: 'success' },
              { label: 'IP', value: 1, color: 'success' },
            ],
          }),
          addForm: { value: 0 },
          form: {
            rules: [{ required: true, message: '限流类型不能为空' }],
            helper: '全局表示所有IP访问,IP表示每隔间断访问',
          },
        },
        total: {
          title: '数量',
          type: 'number',
          column: { width: 100 },
          addForm: { value: 10 },
          form: {
            component: { min: 10, max: 9_999_999 },
            rules: [{ required: true, message: '限流数量不能为空' }],
          },
        },
        visits: {
          title: '访问量',
          type: 'number',
          column: { width: 120 },
          form: { show: false },
        },
        status: {
          title: '状态',
          type: 'dict-radio',
          column: { width: 120 },
          addForm: { value: true },
          search: { show: true },
          dict: statusDict(),
        },
        blacklist: {
          title: '进黑名单',
          type: 'dict-radio',
          column: { width: 120 },
          addForm: { value: true },
          search: { show: true },
          dict: booleanDict(),
        },
        method: {
          title: '方法',
          type: 'dict-select',
          column: { width: 120 },
          search: { show: true },
          dict: httpMethodDict(),
          form: { rules: [{ required: true, message: '请选择拦截方法' }] },
        },
        path: {
          title: '路径',
          type: 'text',
          column: { ellipsis: true },
          form: { helper: '如果为空默认拦截所有' },
        },
        dateTimeRange: { ...datetimeRangeColumn, form: { col: { span: 24 } } },
        description: descriptionColumn,
        createTime: createTimeColumn,
      },
    },
  };
}
