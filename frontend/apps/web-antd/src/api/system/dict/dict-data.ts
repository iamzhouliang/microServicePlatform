/**
 * 获取字典明细（按字典名）
 *
 * @deprecated 占位实现：返回空数组会污染 store 缓存，禁止使用。
 * TODO: replace with real backend call `GET /iam/dict/{name}/items`
 * @param _dictName 字典名称
 */
export function dictDataInfo(_dictName: string): Promise<never[]> {
  return Promise.reject(
    new Error(
      'dictDataInfo is a placeholder. Replace with real backend implementation before use.',
    ),
  );
}