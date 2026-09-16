/**
 * 代码生成表配置 - CRUD 选项定义
 * 用于配置表结构、生成规则和模板组
 */
import type {
  AddReq,
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
  DelReq,
  EditReq,
} from '@fast-crud/fast-crud';

import { dict } from '@fast-crud/fast-crud';

import { createBooleanDict, createTimeColumn, hiddenIdColumn } from '../shared';
import * as groupApi from '../template-group/api';
import { createGroupCrudForSelect } from '../template-group/crud';
import * as api from './api';

export default function createCrudOptions(
  props: CreateCrudOptionsProps,
): CreateCrudOptionsRet {
  const { showModalPre } = props.context;

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
        width: 300,
        fixed: 'right',
        buttons: {
          view: { show: false },
          download: {
            type: 'link',
            text: '代码生成',
            size: 'small',
            title: '代码生成',
            async click(context) {
              await api.DownloadFile(context.row.id);
            },
          },
          preview: {
            type: 'link',
            text: '代码预览',
            size: 'small',
            title: '代码预览',
            click(context) {
              showModalPre(context.row.id);
            },
          },
          remove: { order: 2 },
        },
      },

      columns: {
        name: {
          title: '表名',
          type: 'text',
          column: { width: 200, ellipsis: true },
          search: { show: true },

          editForm: {
            component: { disabled: true },
          },
        },

        packageName: {
          title: '包名',
          type: 'text',
          column: { width: 200 },
          form: {
            col: { span: 24 },
            rules: [{ required: true, message: '包名不能为空' }],
          },
        },
        moduleName: {
          title: '模块名',
          type: 'text',
          column: { width: 160 },
          form: {
            col: { span: 24 },
            rules: [{ required: true, message: '模块名不能为空' }],
          },
        },

        author: {
          title: '作者',
          type: 'text',
          column: { width: 150, ellipsis: true },
          form: {
            show: false,
            rules: [{ required: true, message: '作者不能为空' }],
            helper: '默认当前登录人昵称',
          },
        },
        className: {
          title: '类名',
          type: 'text',
          column: { width: 200 },
          form: {
            show: false,
            rules: [{ required: true, message: '类名不能为空' }],
          },
        },
        businessName: {
          title: '业务名',
          type: 'text',
          column: { width: 200 },
          form: { rules: [{ required: true, message: '业务名不能为空' }] },
        },
        removePrefix: {
          title: '忽略前缀',
          addForm: { value: false },
          column: { show: true, width: 100 },
          type: ['dict-radio'],
          dict: createBooleanDict(),
          form: { rules: [{ required: true, message: '不能为空' }] },
        },
        prefix: {
          title: '前缀名',
          type: 'text',
          column: { width: 100 },
        },
        templateGroupId: {
          title: '模板组',
          search: { show: false },
          type: 'table-select',
          dict: dict({
            value: 'id',
            label: 'name',
            getNodesByValues: async (values: any[]) => {
              if (!values || values.length === 0) return [];
              const allList = await groupApi.GetList();
              // 单选：根据选中的 ID 过滤返回对应的模板组数据
              return allList.filter((item: any) => values.includes(item.id));
            },
          }),
          form: {
            component: {
              createCrudOptions: createGroupCrudForSelect,
              crudOptionsOverride: {
                table: { scroll: { x: 600 } },
              },
            },
          },
          column: { width: 150, show: true, component: { color: 'auto' } },
        },
        comment: {
          title: '表描述',
          type: 'textarea',
          column: { width: 200, ellipsis: true },
          form: {
            col: { span: 24 },
            rules: [{ required: true, message: '表描述不能为空' }],
          },
        },
        id: hiddenIdColumn,
        createTime: createTimeColumn,
      },
    },
  };
}
