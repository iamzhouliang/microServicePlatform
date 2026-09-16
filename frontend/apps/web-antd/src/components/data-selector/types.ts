/**
 * 数据选择器通用类型定义
 * 架构设计：支持扁平数据和树形数据的统一选择交互
 */

/** 基础数据项 */
export interface DataItem {
  id: number | string;
  name: string;
  parentId?: number | string;
  children?: DataItem[];
  disabled?: boolean;
}

/** 选择器模式 */
export type SelectorMode = 'flat' | 'tree';

/** 选择器配置 */
export interface DataSelectorConfig {
  /** 选择器模式：flat-穿梭框，tree-树选择 */
  mode: SelectorMode;
  /** 标题 */
  title: string;
  /** 是否多选 */
  multiple?: boolean;
  /** 搜索占位符 */
  searchPlaceholder?: string;
  /** 是否显示搜索框 */
  showSearch?: boolean;
  /** 每页条数（仅扁平模式） */
  pageSize?: number;
  /** 树形数据是否级联选择 */
  checkStrictly?: boolean;
  /** 数据加载函数 */
  loadData?: () => Promise<DataItem[]>;
  /** 远程搜索函数（可选，用于超大数据量） */
  remoteSearch?: (keyword: string) => Promise<DataItem[]>;
}

/** 选择器 Props */
export interface DataSelectorModalProps {
  /** 是否显示 */
  open: boolean;
  /** 配置 */
  config: DataSelectorConfig;
  /** 已选中的值 */
  value?: (number | string)[];
  /** 数据源（如果不使用 loadData） */
  dataSource?: DataItem[];
}

/** 选择器 Emits */
export interface DataSelectorModalEmits {
  (e: 'update:open', value: boolean): void;
  (e: 'update:value', value: (number | string)[]): void;
  (e: 'confirm', value: (number | string)[], items: DataItem[]): void;
  (e: 'cancel'): void;
}
