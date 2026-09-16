/**
 * 字典明细（与后端 SysDictData 实体对齐）
 * 注：租户隔离由后端按当前会话租户过滤，不在字段层冗余表达
 */
export interface DictData {
  id: number | string;
  label: string;
  value: number | string;
  /** 后端原始值字段（与 value 字段冗余，store/dict.ts 等处仍在使用） */
  dictValue?: number | string;
  cssClass?: string;
  listClass?: string;
  status?: number | string;
  remark?: string;
  createdTime?: string;
  updatedTime?: string;
}