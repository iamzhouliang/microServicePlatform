/**
 * 全局 ambient 类型声明
 */

/**
 * `tinymce` 通过 `<script>` 注入到 window（见 `editor.vue` 的 `tinymceScriptSrc`），
 * npm 中没有独立 `@types/tinymce`，只暴露运行时对象。这里扩展 `tinymce/tinymce`
 * 子路径导入（`@tinymce/tinymce-vue` 的 Editor 组件用）以消除类型错误。
 *
 * 如未来启用 build-time tinymce，可改用 `import type { Editor } from 'tinymce'`。
 */
declare module 'tinymce/tinymce' {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  export type Editor = any;
}

/**
 * 扩展标准 DOM File 接口：TypeScript DOM lib 未声明 `webkitRelativePath`，
 * 但拖拽文件夹上传等场景会用到。
 */
interface File {
  webkitRelativePath?: string;
}