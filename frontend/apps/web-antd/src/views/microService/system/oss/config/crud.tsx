import type { CreateCrudOptionsRet } from '@fast-crud/fast-crud';

import { dict } from '@fast-crud/fast-crud';

import { defHttp } from '#/api/request';
import {
  createTimeReadonlyColumn,
  hiddenIdColumn,
  statusDict,
} from '#/plugin/fast-crud/shared';

export default function crud(): CreateCrudOptionsRet {
  return {
    crudOptions: {
      request: {
        pageRequest: async (query: any) =>
          await defHttp.post(`/suite/oss/configs/page`, query),
        addRequest: async ({ form }: any) =>
          await defHttp.post(`/suite/oss/configs/create`, form),
        editRequest: async ({ form }: any) =>
          await defHttp.put(`/suite/oss/configs/${form.id}/modify`, form),
        delRequest: async ({ row }: any) =>
          await defHttp.delete(`/suite/oss/configs/${row.id}`),
      },
      rowHandle: { fixed: 'right' },
      columns: {
        id: hiddenIdColumn,
        type: {
          title: '存储类型',
          type: 'dict-select',
          column: { show: true, width: 180, component: { color: 'auto' } },
          dict: dict({
            data: [
              { value: 's3', label: '标准S3协议' },
              { value: 'minio', label: 'minio' },
              { value: 'aliyun', label: '阿里云' },
              { value: 'qiniu', label: '七牛云' },
              { value: 'tenxun', label: '腾讯云' },
              { value: 'huawei', label: '华为云' },
            ],
          }),
        },
        domain: {
          title: '访问域名',
          type: 'textarea',
          column: { width: 200, ellipsis: true },
          form: {
            col: {
              span: 24,
            },
            helper: '注意“/”结尾，例如：http://minio.abc.com/abc/',
            rules: [{ required: true, message: '请输入访问域名' }],
          },
        },
        bucketName: {
          title: '桶名称',
          type: 'textarea',
          column: { width: 160, ellipsis: true },
          form: {
            col: {
              span: 24,
            },
            rules: [{ required: true, message: '请输入桶名称' }],
          },
        },
        accessKey: {
          title: 'AccessKey',
          type: 'textarea',
          column: { show: true, width: 240, ellipsis: true },
          form: {
            col: {
              span: 24,
            },
            rules: [{ required: true, message: '请输入访问key' }],
          },
        },
        secretKey: {
          title: 'SecretKey',
          type: ['password'],
          column: { show: false, width: 120, ellipsis: true },
          form: {
            col: {
              span: 24,
            },
            rules: [{ required: true, message: '请输入SecretKey' }],
          },
        },
        basePath: {
          title: '基础路径',
          type: 'text',
          column: { width: 160, ellipsis: true },
          form: {
            helper: '例如:test/\n文件路径由访问域名和基础路径拼接而成',
            rules: [{ required: true, message: '请输入基础路径' }],
          },
        },
        endPoint: {
          title: '连接终端',
          type: 'textarea',
          column: { width: 200, ellipsis: true },
          form: {
            col: {
              span: 24,
            },
            helper: '例如:http://host:15000',
            rules: [{ required: true, message: '请输入endPoint' }],
          },
        },
        status: {
          title: '状态',
          search: { show: true },
          addForm: { value: false },
          column: { show: true, width: 100 },
          type: ['dict-radio'],
          dict: statusDict(),
        },
        createTime: createTimeReadonlyColumn,
      },
    },
  };
}
