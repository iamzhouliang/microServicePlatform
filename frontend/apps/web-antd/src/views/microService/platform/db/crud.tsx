import type {
  CreateCrudOptionsProps,
  CreateCrudOptionsRet,
} from '@fast-crud/fast-crud';

import { dict } from '@fast-crud/fast-crud';

import { defHttp } from '#/api/request';
import {
  createTimeReadonlyColumn,
  hiddenIdColumn,
} from '#/plugin/fast-crud/shared';

/** 数据库配置 */
const DB_CONFIG: Record<
  string,
  {
    color: string;
    defaultPort: number;
    driver: string;
    label: string;
    urlTemplate: string;
  }
> = {
  mysql: {
    label: 'MySQL',
    driver: 'com.mysql.cj.jdbc.Driver',
    defaultPort: 3306,
    urlTemplate:
      'jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai',
    color: 'processing',
  },
  postgresql: {
    label: 'PostgreSQL',
    driver: 'org.postgresql.Driver',
    defaultPort: 5432,
    urlTemplate: 'jdbc:postgresql://{host}:{port}/{database}',
    color: 'success',
  },
  oracle: {
    label: 'Oracle',
    driver: 'oracle.jdbc.OracleDriver',
    defaultPort: 1521,
    urlTemplate: 'jdbc:oracle:thin:@{host}:{port}:{database}',
    color: 'warning',
  },
  sqlserver: {
    label: 'SQL Server',
    driver: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
    defaultPort: 1433,
    urlTemplate: 'jdbc:sqlserver://{host}:{port};DatabaseName={database}',
    color: 'error',
  },
  dm: {
    label: '达梦',
    driver: 'dm.jdbc.driver.DmDriver',
    defaultPort: 5236,
    urlTemplate: 'jdbc:dm://{host}:{port}/{database}',
    color: 'cyan',
  },
  kingbase: {
    label: '人大金仓',
    driver: 'com.kingbase8.Driver',
    defaultPort: 54_321,
    urlTemplate: 'jdbc:kingbase8://{host}:{port}/{database}',
    color: 'purple',
  },
};

/** 数据库类型字典 */
const dbTypeDict = Object.entries(DB_CONFIG).map(
  ([value, { label, color }]) => ({
    value,
    label,
    color,
  }),
);

/** 根据配置生成 JDBC URL */
function buildJdbcUrl(
  dbType: string,
  host: string,
  port: number,
  database: string,
): string {
  const config = DB_CONFIG[dbType];
  if (!config || !host || !database) return '';
  return config.urlTemplate
    .replace('{host}', host)
    .replace('{port}', String(port || config.defaultPort))
    .replace('{database}', database);
}

/** 从 JDBC URL 解析连接信息 */
function parseJdbcUrl(
  jdbcUrl: string,
  dbType: string,
): null | { database: string; host: string; port: number } {
  if (!jdbcUrl || !dbType) return null;
  const config = DB_CONFIG[dbType];
  if (!config) return null;

  try {
    // 通用解析: 匹配 host:port/database 或 host:port;DatabaseName=database
    const patterns = [
      /\/\/([^:/]+):(\d+)\/([^?;]+)/, // mysql/postgresql/dm/kingbase
      /@([^:/]+):(\d+):(\w+)/, // oracle
      /\/\/([^:/]+):(\d+);DatabaseName=(\w+)/i, // sqlserver
    ];
    for (const pattern of patterns) {
      const match = jdbcUrl.match(pattern);
      if (match && match[1] && match[2] && match[3]) {
        return {
          host: match[1],
          port: Number.parseInt(match[2], 10),
          database: match[3],
        };
      }
    }
  } catch {
    // 解析失败返回 null
  }
  return null;
}

/** 更新表单中的 jdbcUrl */
function updateJdbcUrl(form: any) {
  if (form.dbType && form._host && form._database) {
    form.jdbcUrl = buildJdbcUrl(
      form.dbType,
      form._host,
      form._port,
      form._database,
    );
  }
}

export default function crud(
  _props: CreateCrudOptionsProps,
): CreateCrudOptionsRet {
  return {
    crudOptions: {
      request: {
        pageRequest: async (query: any) =>
          await defHttp.get('/iam/db-instances/page', { params: query }),
        addRequest: async ({ form }: any) =>
          await defHttp.post('/iam/db-instances/create', form),
        editRequest: async ({ form }: any) =>
          await defHttp.put(`/iam/db-instances/${form.id}/modify`, form),
        delRequest: async ({ row }: any) =>
          await defHttp.delete(`/iam/db-instances/${row.id}`),
      },
      rowHandle: { fixed: 'right', width: 170 },
      columns: {
        id: hiddenIdColumn,
        name: {
          title: '连接名称',
          type: 'text',
          search: { show: true },
          column: { width: 230 },
          form: {
            rules: [
              { required: true, message: '请输入连接名称' },
              { max: 50, message: '长度不能超过50个字符' },
            ],
          },
        },
        dbType: {
          title: '数据库类型',
          type: 'dict-select',
          search: { show: true },
          column: { width: 120, align: 'center' },
          addForm: { value: 'mysql' },
          dict: dict({ data: dbTypeDict }),
          form: {
            rules: [{ required: true, message: '请选择数据库类型' }],
            valueChange({ form, value }: any) {
              if (value && DB_CONFIG[value]) {
                const config = DB_CONFIG[value];
                form.driverClassName = config.driver;
                form._port = config.defaultPort;
                updateJdbcUrl(form);
              }
            },
          },
        },
        _host: {
          title: '主机地址',
          type: 'text',
          column: { show: false },
          addForm: { value: 'localhost' },
          form: {
            rules: [{ required: true, message: '请输入主机地址' }],
            helper: 'IP地址或域名',
            valueChange({ form }: any) {
              updateJdbcUrl(form);
            },
          },
          valueBuilder({ row }: any) {
            const parsed = parseJdbcUrl(row.jdbcUrl, row.dbType);
            if (parsed) row._host = parsed.host;
          },
        },
        _port: {
          title: '端口',
          type: 'number',
          column: { show: false },
          addForm: { value: 3306 },
          form: {
            rules: [{ required: true, message: '请输入端口' }],
            valueChange({ form }: any) {
              updateJdbcUrl(form);
            },
          },
          valueBuilder({ row }: any) {
            const parsed = parseJdbcUrl(row.jdbcUrl, row.dbType);
            if (parsed) row._port = parsed.port;
          },
        },
        _database: {
          title: '数据库名',
          type: 'text',
          column: { show: false },
          form: {
            rules: [{ required: true, message: '请输入数据库名' }],
            valueChange({ form }: any) {
              updateJdbcUrl(form);
            },
          },
          valueBuilder({ row }: any) {
            const parsed = parseJdbcUrl(row.jdbcUrl, row.dbType);
            if (parsed) row._database = parsed.database;
          },
        },
        jdbcUrl: {
          title: 'JDBC连接串',
          type: 'textarea',
          column: { width: 300, ellipsis: true },
          form: {
            col: { span: 24 },
            component: { disabled: true },
            helper: '根据上方配置自动生成',
          },
        },
        username: {
          title: '用户名',
          type: 'text',
          column: { width: 120 },
          form: {
            rules: [{ required: true, message: '请输入用户名' }],
          },
        },
        password: {
          title: '密码',
          type: 'password',
          column: { show: false },
          form: {
            rules: [{ required: true, message: '请输入密码' }],
          },
        },
        driverClassName: {
          title: '驱动类名',
          type: 'text',
          column: { width: 220, ellipsis: true },
          addForm: { value: 'com.mysql.cj.jdbc.Driver' },
          form: {
            component: { disabled: true },
            helper: '根据数据库类型自动填充',
          },
        },
        status: {
          title: '状态',
          type: 'dict-radio',
          search: { show: true },
          column: { width: 80, align: 'center' },
          addForm: { value: true },
          dict: dict({
            data: [
              { value: true, label: '启用', color: 'success' },
              { value: false, label: '停用', color: 'error' },
            ],
          }),
        },
        description: {
          title: '描述',
          type: 'textarea',
          column: { show: false },
          form: { col: { span: 24 } },
        },
        createdTime: createTimeReadonlyColumn,
      },
    },
  };
}
