import { computed, ref } from 'vue';

import { FsFormWrapper, useFs } from '@fast-crud/fast-crud';
import { message, Modal } from 'ant-design-vue';

import * as api from './api';
import createFormOptions from './dict';
import createCrudOptions from './dict-item-crud';

export interface DictNode {
  id: number | string;
  code: string;
  type: number;
  name: string;
  sequence: number;
  description?: string;
  parentId?: number | string;
  children?: DictNode[];
}

export function useDictPage() {
  function useFormWrapperUsingTag(callback: any) {
    const formWrapperRef = ref<any | InstanceType<typeof FsFormWrapper>>();
    const formWrapperOptions = ref<any>();
    formWrapperOptions.value = createFormOptions(callback);
    const initData = {
      type: 0,
      sequence: 99,
      parentId: 0,
    };
    function openFormWrapper(form: any) {
      formWrapperOptions.value.initialForm = form || initData;
      formWrapperOptions.value.columns.code.component.disabled = false;
      formWrapperRef.value.open(formWrapperOptions.value);
    }

    return {
      formWrapperRef,
      openFormWrapper,
      formWrapperOptions,
    };
  }

  const treeData = ref<DictNode[]>([]);
  const loading = ref(true);

  const loadDictList = async () => {
    loading.value = true;
    try {
      const ret = await api.GetList();
      treeData.value = ret;
    } finally {
      loading.value = false;
    }
  };

  /** 刷新字典缓存 */
  const refreshCache = async () => {
    await api.Refresh();
    await loadDictList();
    message.success('字典缓存刷新成功');
  };

  const { formWrapperRef, openFormWrapper, formWrapperOptions } =
    useFormWrapperUsingTag(() => loadDictList());

  const { crudBinding, crudRef, crudExpose } = useFs({
    createCrudOptions,
    context: { permission: 'dict' },
  });

  const handleMenuClick = (node: DictNode) => {
    selectedKeys.value = [node.id];
    // fast-crud 的 CrudBinding 类型未公开深层属性，用 any 越过类型访问
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const crudBindRef = crudBinding.value as any;
    const initialForm = { parentId: node.id, parentCode: node.code };
    crudBindRef.search.initialForm = initialForm;
    crudBindRef.addForm.initialForm = initialForm;
    crudBindRef.actionbar.buttons.add.show = true;
    crudExpose.setSearchFormData({ form: { ...initialForm } });
    crudExpose.doRefresh();
  };

  const handleEdit = (node: DictNode) => {
    const initForm = {
      id: node.id,
      code: node.code,
      type: node.type,
      name: node.name,
      sequence: node.sequence,
      description: node.description,
    };
    formWrapperOptions.value.columns.code.component.disabled = true;
    openFormWrapper(initForm);
  };

  /** 清空右侧字典子项列表 */
  const clearDictItems = () => {
    selectedKeys.value = [];
    // fast-crud 的 CrudBinding 类型未公开深层属性，用 any 越过类型访问
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const crudBindRef = crudBinding.value as any;
    crudBindRef.actionbar.buttons.add.show = false;
    crudBindRef.search.initialForm = { parentId: null };
    crudBindRef.addForm.initialForm = { parentId: null };
    crudExpose.setSearchFormData({ form: { parentId: undefined } });
    crudExpose.doRefresh();
  };

  const handleDelete = (node: DictNode) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定删除「${node.name}」吗？会级联删除子节点以及相关资源数据`,
      okType: 'danger',
      onOk: async () => {
        await api.DelObj(String(node.id));
        await loadDictList();
        clearDictItems();
        message.success('删除成功');
      },
    });
  };

  const searchText = ref('');
  const selectedKeys = ref<(number | string)[]>([]);

  const filteredTree = computed(() => {
    const keyword = searchText.value.trim().toLowerCase();
    if (!keyword) return treeData.value;
    return treeData.value.filter(
      (n) =>
        n.name?.toLowerCase().includes(keyword) ||
        n.code?.toLowerCase().includes(keyword),
    );
  });

  return {
    // state
    treeData,
    loading,
    searchText,
    selectedKeys,
    filteredTree,
    // crud
    crudBinding,
    crudRef,
    crudExpose,
    // form wrapper
    formWrapperRef,
    openFormWrapper,
    formWrapperOptions,
    // actions
    loadDictList,
    refreshCache,
    handleMenuClick,
    handleEdit,
    handleDelete,
  };
}

export type UseDictReturn = ReturnType<typeof useDictPage>;
