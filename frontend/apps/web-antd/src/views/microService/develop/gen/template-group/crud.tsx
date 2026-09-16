/**
 * 代码生成模板分组 - CRUD 选项定义
 * 用于管理模板分组，一个分组可以包含多个模板
 */
import type {
  AddReq,
  CreateCrudOptionsRet,
  DelReq,
  EditReq,
} from '@fast-crud/fast-crud';

import { dict } from '@fast-crud/fast-crud';

import { createBooleanDict, createTimeColumn, hiddenIdColumn } from '../shared';
import * as templateApi from '../template/api';
import { createCrudOptionsForSelect as createTemplateCrudForSelect } from '../template/crud';
import * as api from './api';

/**
 * 用于 table-select 的简化版 CRUD 配置
 * 不依赖 context，只用于选择器弹窗
 */
export function createGroupCrudForSelect(): CreateCrudOptionsRet {
  return {
    crudOptions: {
      request: {
        pageRequest: api.GetPage,
      },
      table: { scroll: { fixed: true } },
      actionbar: { buttons: { add: { show: false } } },
      rowHandle: { show: false },
      columns: {
        id: hiddenIdColumn,
        name: {
          title: '模板分组',
          type: 'text',
          column: { width: 180, ellipsis: true },
          search: { show: true },
        },
        description: {
          title: '分组描述',
          type: 'text',
          column: { width: 250, ellipsis: true },
        },
      },
    },
  };
}

export default function createCrudOptions(): CreateCrudOptionsRet {
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
          add: {},
        },
      },
      rowHandle: {
        width: 180,
        align: 'center',
        fixed: 'right',
        buttons: {
          remove: { order: 2 },
        },
      },
      columns: {
        id: hiddenIdColumn,
        name: {
          title: '模板分组',
          type: 'text',
          column: { width: 160, ellipsis: true },
          search: { show: true },
          form: {
            rules: [{ required: true, message: '分组名称不能为空' }],
          },
        },
        description: {
          title: '分组描述',
          type: 'text',
          column: { width: 200, ellipsis: true },
        },
        templateIds: {
          title: '模板',
          search: { show: false },
          type: 'table-select',
          column: { width: 580, show: true, component: { color: 'auto' } },
          dict: dict({
            value: 'id',
            label: 'name',
            getNodesByValues: async (values: any[]) => {
              if (!values || values.length === 0) return [];
              const allList = await templateApi.GetList();
              // 根据选中的 ID 过滤返回对应的模板数据
              return allList.filter((item: any) => values.includes(item.id));
            },
          }),
          form: {
            col: { span: 24 },
            component: {
              multiple: true,
              createCrudOptions: createTemplateCrudForSelect,
              crudOptionsOverride: {
                table: { scroll: { x: 800 } },
              },
            },
            rules: [{ required: true, message: '模板不能为空' }],
          },
        },
        isDefault: {
          title: '是否默认',
          column: { width: 100, ellipsis: true },
          type: ['dict-radio'],
          dict: createBooleanDict(),
          addForm: { value: false },
        },
        createTime: createTimeColumn,
      },
    },
  };
}
