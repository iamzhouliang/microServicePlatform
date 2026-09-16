import type { CreateCrudOptionsProps } from '@fast-crud/fast-crud';

import { defHttp } from '#/api/request';
import {
  booleanDict,
  createTimeReadonlyColumn,
  hiddenIdColumn,
} from '#/plugin/fast-crud/shared';

export default function crud(props: CreateCrudOptionsProps) {
  const { openDesignModal } = props.context;

  return {
    crudOptions: {
      request: {
        pageRequest: async (query: any) =>
          await defHttp.post('/suite/online-model/page', query),
        addRequest: async ({ form }: any) =>
          await defHttp.post('/suite/online-model/create', form),
        editRequest: async ({ form }: any) =>
          await defHttp.put(`/suite/online-model/${form.id}/modify`, form),
        delRequest: async ({ row }: any) =>
          await defHttp.delete(`/suite/online-model/${row.id}`),
      },
      table: {
        scroll: { fixed: true },
      },
      rowHandle: {
        width: 250,
        fixed: 'right',
        buttons: {
          formDesigner: {
            type: 'link',
            text: '表单设计',
            size: 'small',
            title: '表单设计',
            click({ row }: any) {
              openDesignModal?.(row);
            },
          },
          remove: { order: 2 },
        },
      },
      columns: {
        id: hiddenIdColumn,
        definitionKey: {
          title: '定义KEY',
          type: 'text',
          column: { width: 200 },
          search: { show: true },
          form: {
            col: { span: 24 },
            wrapperCol: { span: 9 },
            rules: [{ required: true, message: '定义KEY不能为空' }],
          },
        },
        title: {
          title: '标题',
          type: 'text',
          search: { show: true },
          column: { width: 200, ellipsis: true },
          form: {
            rules: [{ required: true, message: '标题不能为空' }],
          },
        },
        status: {
          title: '状态',
          type: 'dict-radio',
          form: { value: true },
          dict: booleanDict(),
          column: { width: 100 },
        },
        description: {
          title: '描述',
          type: ['textarea'],
          column: { width: 230, ellipsis: true },
          form: {
            col: { span: 24 },
            rules: [{ required: true, message: '描述不能为空' }],
          },
        },
        createTime: createTimeReadonlyColumn,
      },
    },
  };
}
