/**
 * 菜单/资源树节点（与后端 IamResource 实体对齐）
 *
 * 保留 `children` 字段但不再使用 `[key: string]: any` 的索引签名——
 * 这样未知字段会触发类型检查而非静默放行，调用方按需 `as` 显式扩展。
 */
export interface MenuOption {
  id: number | string;
  parentId?: number | string;
  label?: string;
  icon?: string;
  menuType?: 'C' | 'F' | 'M';
  permissions?: Array<{
    id: number | string;
    label: string;
    permission?: string;
  }>;
  children?: MenuOption[];
  /** 其它后端字段，调用方按需处理 */
  meta?: Record<string, unknown>;
}