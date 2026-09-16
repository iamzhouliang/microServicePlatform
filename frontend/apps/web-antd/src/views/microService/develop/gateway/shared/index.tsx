/**
 * 网关管理模块 - 共享配置
 * 从全局共享配置导出，并补充模块特有配置
 */

// 从全局共享配置导出通用配置
export {
  booleanDict,
  createTimeReadonlyColumn as createTimeColumn,
  datetimeRangeColumn,
  descriptionColumn,
  hiddenIdColumn,
  httpMethodDict,
  statusDict,
} from '#/plugin/fast-crud/shared';
