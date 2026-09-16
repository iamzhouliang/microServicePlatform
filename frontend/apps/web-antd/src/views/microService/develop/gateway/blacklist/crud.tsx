/**
 * 网关黑名单 - CRUD 配置
 */
import type {
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
} from '@fast-crud/fast-crud';

import * as _ from 'lodash-es';

import {
  createTimeColumn,
  datetimeRangeColumn,
  descriptionColumn,
  httpMethodDict,
  statusDict,
} from '../shared/index';
import * as api from './api';

export default function crud({
  context,
}: CreateCrudOptionsProps): CreateCrudOptionsRet {
  const localDataRef = context.localDataRef;

  /** 本地分页查询 */
  const pageRequest = async (query: any) => {
    let data = localDataRef.value.data;
    const { current, offset, size, status, ip, method, path } = query;

    // 本地过滤
    data = data.filter((item: any) => {
      if (status && item.status !== status) return false;
      if (ip && !item.ip.includes(ip)) return false;
      if (method && !item.method.includes(method)) return false;
      if (path && !item.path.includes(path)) return false;
      return true;
    });

    // 本地分页
    const end = Math.min(offset + size, data.length);
    return {
      current,
      offset,
      size,
      total: data.length,
      records: data.slice(offset, end),
    };
  };

  /** 编辑请求 */
  const editRequest = async ({ form, row }: any) => {
    form.id = row.id;
    await api.UpdateObj(form);
    localDataRef.value.forEach(
      (item: any) => item.id === form.id && _.merge(item, form),
    );
  };

  /** 新增请求 */
  const addRequest = async ({ form }: any) => {
    const id = await api.AddObj(form);
    form.id = id;
    localDataRef.value.unshift(form);
    return id;
  };

  /** 删除请求 */
  const delRequest = async ({ row }: any) => {
    await api.DelObj(row.id);
    const idx = localDataRef.value.findIndex((item: any) => item.id === row.id);
    if (idx !== -1) localDataRef.value.splice(idx, 1);
  };

  return {
    crudOptions: {
      request: { pageRequest, addRequest, editRequest, delRequest },
      columns: {
        ip: {
          title: 'IP',
          type: 'text',
          column: { width: 160 },
          search: { show: true },
        },
        status: {
          title: '状态',
          type: 'dict-radio',
          addForm: { value: true },
          column: { width: 100 },
          search: { show: true },
          dict: statusDict(),
        },
        visits: {
          title: '访问量',
          type: 'text',
          column: { width: 80 },
          form: { show: false },
        },
        path: {
          title: '路径',
          type: 'text',
          search: { show: true },
          column: { width: 150, ellipsis: true },
          form: {
            helper: '支持 ?, *, ** 通配符匹配，网关中需要去除微服务前缀',
          },
        },
        method: {
          title: '方法',
          type: 'dict-select',
          search: { show: true },
          column: { width: 100 },
          dict: httpMethodDict(),
          addForm: { value: 'ALL' },
          form: { rules: [{ required: true, message: '请选择拦截方法' }] },
        },
        datetimerange: datetimeRangeColumn,
        description: descriptionColumn,
        createTime: createTimeColumn,
      },
    },
  };
}
