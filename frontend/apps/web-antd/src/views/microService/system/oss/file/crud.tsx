import type {
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
  EditReq,
  ValueBuilderContext,
} from '@fast-crud/fast-crud';

import { computed } from 'vue';

import { notification } from 'ant-design-vue';
import dayjs from 'dayjs';

import { defHttp } from '#/api/request';

import { downloadFile } from './api';

export default function crud(
  props: CreateCrudOptionsProps,
): CreateCrudOptionsRet {
  const { showTableComputed, selectedCategory, onPreview } = props.context;
  return {
    crudOptions: {
      request: {
        transformQuery: ({ page, form, sort }: any) => {
          const order =
            sort === null ? {} : { column: sort.prop, asc: sort.asc };
          const currentPage = page.currentPage ?? 1;
          const limit = page.pageSize ?? 10;
          const offset = limit * (currentPage - 1);
          return {
            offset,
            current: currentPage,
            size: limit,
            ...form,
            ...order,
          };
        },
        pageRequest: async (query: any) => {
          // 使用选中的分类进行筛选
          query.category = selectedCategory?.value || null;
          return await defHttp.get(`/suite/oss/files`, {
            params: query,
          });
        },
        editRequest: async ({ form }: EditReq) => {
          await defHttp.put(`/suite/oss/files/${form.id}/name`, {
            name: form.originalFilename,
          });
        },
        delRequest: async ({ row }: any) =>
          await defHttp.delete(`/suite/oss/files/${row.id}`),
      },
      toolbar: {},
      actionbar: {
        show: true,
        buttons: {
          add: {
            show: false,
            icon: 'codicon:repo-force-push',
            text: '文件上传',
            async click() {
              notification.error({
                message: '暂未实现',
                duration: 3,
              });
            },
          },
        },
      },
      table: {
        // 不使用 show 控制，改用 CSS 隐藏，避免 DOM 被移除导致 doRefresh 报错
        class: computed(() => (showTableComputed.value ? '' : 'hidden')),
      },
      rowHandle: {
        width: 180,
        buttons: {
          add: { show: false },
          view: { show: false },
          edit: {
            show: false,
            text: '重命名',
            title: '重命名',
          },
          preview: {
            type: 'link',
            text: '预览',
            size: 'small',
            title: '文件预览',
            order: 0,
            click(context: any) {
              onPreview?.(context.row);
            },
          },
          download: {
            type: 'link',
            text: '下载',
            size: 'small',
            title: '文件下载',
            order: 1,
            click(context: any) {
              notification.info({
                message: '开始下载',
                duration: 3,
              });
              downloadFile(context.row.url, context.row.originalFilename);
            },
          },
          remove: { order: 2 },
        },
      },
      columns: {
        id: {
          title: 'ID',
          type: 'text',
          form: { show: false },
          column: { show: false },
        },
        originalFilename: {
          title: '原始名',
          type: 'text',
          column: { ellipsis: true, width: 230 },
          search: { show: true },
        },
        storagePath: {
          title: '实际对象路径',
          type: 'text',
          column: { ellipsis: true, width: 260 },
          form: { show: false },
        },
        formatSize: {
          title: '文件大小',
          type: 'text',
          form: { show: false },
          column: { ellipsis: true, width: 100 },
        },
        platform: {
          title: '存储平台',
          type: 'text',
          form: { show: false },
          column: { ellipsis: true, width: 150, show: true },
        },
        createName: {
          title: '上传者',
          type: 'text',
          form: { show: false },
          search: { show: true },

          column: { ellipsis: true, width: 150 },
        },
        url: {
          title: '预览',
          column: { ellipsis: true, width: 150 },
          form: { show: false },
        },
        createTime: {
          title: '上传时间',
          type: 'datetime',
          form: { show: false },
          column: { ellipsis: true, width: 180 },
          valueBuilder({ value, row, key }: ValueBuilderContext): void {
            if (value !== null) {
              row[key] = dayjs(value);
            }
          },
        },
      },
    },
  };
}
