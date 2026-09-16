/**
 * 代码生成模板 - CRUD 选项定义
 * 用于管理代码生成模板（如 Entity、Controller、Service 等）
 */
import type {
  AddReq,
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
  DelReq,
  EditReq,
} from '@fast-crud/fast-crud';

import { createTimeColumn, hiddenIdColumn } from '../shared';
import * as api from './api';

/**
 * 用于 table-select 的简化版 CRUD 配置
 * 不依赖 context，只用于选择器弹窗
 */
export function createCrudOptionsForSelect(): CreateCrudOptionsRet {
  return {
    crudOptions: {
      request: { pageRequest: api.GetPage },
      table: { scroll: { fixed: true } },
      actionbar: { buttons: { add: { show: false } } },
      rowHandle: { show: false },
      columns: {
        id: hiddenIdColumn,
        name: {
          title: '模板名称',
          type: 'text',
          column: { width: 180, ellipsis: true },
          search: { show: true },
        },
        generatePath: {
          title: '模板路径',
          type: 'text',
          column: { width: 650, ellipsis: true },
        },
        description: {
          title: '模板描述',
          type: 'text',
          column: { width: 200, ellipsis: true },
        },
      },
    },
  };
}

export default function createCrudOptions(
  props: CreateCrudOptionsProps,
): CreateCrudOptionsRet {
  const { showEditModal, showViewModal } = props.context;

  return {
    crudOptions: {
      request: {
        pageRequest: async (query: any) => await api.GetPage(query),
        addRequest: async ({ form }: AddReq) => await api.AddObj(form),
        editRequest: async ({ form }: EditReq) => await api.UpdateObj(form),
        delRequest: async ({ row }: DelReq) => await api.DelObj(row.id),
      },
      table: {
        scroll: { fixed: true },
      },
      actionbar: {
        buttons: {
          add: {
            show: false,
          },
        },
      },
      rowHandle: {
        width: 180,
        fixed: 'right',
        align: 'center',
        buttons: {
          view: {
            async click(context) {
              showViewModal(context.row.id);
            },
          },
          edit: {
            async click(context) {
              showEditModal(context.row.id);
            },
          },
          remove: { order: 2 },
        },
      },
      columns: {
        id: hiddenIdColumn,
        name: {
          title: '模板名称',
          type: 'text',
          column: { width: 200, ellipsis: true },
          search: { show: true },
        },
        generatePath: {
          title: '模板路径',
          type: 'text',
          column: { width: 400, ellipsis: true },
        },
        description: {
          title: '模板描述',
          type: 'text',
          column: { width: 180, ellipsis: true },
        },
        createName: {
          title: '创建人',
          type: 'text',
          column: { width: 150, ellipsis: true },
        },
        createTime: createTimeColumn,
      },
    },
  };
}
