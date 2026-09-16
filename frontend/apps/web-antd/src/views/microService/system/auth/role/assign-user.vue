<script lang="ts" setup name="AssignUser">
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { difference } from 'lodash-es';

import * as api from './api';

const tableColumns = [
  { dataIndex: 'nickname', title: '名称' },
  { dataIndex: 'username', title: '账号' },
];
const showSearch = ref(true);
const leftColumns = ref(tableColumns);
const rightColumns = ref(tableColumns);
const userRoleDetails = ref([]);
const targetKeys = ref<string[]>([]);
const modelRef = ref({
  roleId: null,
  userRoleDetails: [],
  originTargetKeys: [],
}) as Record<string, any>;
const [Modal, modalApi] = useVbenModal({
  title: '分配用户',
  class: 'm-assign-user',
  draggable: false,
  onCancel() {
    modalApi.close();
  },
  onConfirm() {
    api
      .assignUser({
        roleId: modelRef.value.roleId,
        userIdList: targetKeys.value,
      })
      .then(() => {
        modalApi.close();
      });
  },
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    modelRef.value = modalApi.getData<Record<string, any>>();
    userRoleDetails.value = modelRef.value.userRoleDetails?.map((item: any) => {
      return { key: String(item.id), title: item.nickname, ...item };
    });
    targetKeys.value = modelRef.value.originTargetKeys?.map((key: any) =>
      key.toString(),
    );
  },
});

const onChange = (nextTargetKeys: any) => {
  targetKeys.value = nextTargetKeys;
};
const getRowSelection = ({
  selectedKeys,
  onItemSelectAll,
  onItemSelect,
}: any) => {
  return {
    onSelectAll(selected: any, selectedRows: any) {
      const treeSelectedKeys = selectedRows.map(({ key }: any) => key);
      const diffKeys = selected
        ? difference(treeSelectedKeys, selectedKeys)
        : difference(selectedKeys, treeSelectedKeys);
      onItemSelectAll(diffKeys, selected);
    },
    onSelect({ key }: any, selected: any) {
      onItemSelect(key, selected);
    },
    selectedRowKeys: selectedKeys,
  };
};

// 点击行直接移动
const getCustomRow = (direction: 'left' | 'right') => {
  return (record: any) => ({
    onClick: () => {
      if (direction === 'left') {
        if (!targetKeys.value.includes(record.key)) {
          targetKeys.value = [...targetKeys.value, record.key];
        }
      } else {
        targetKeys.value = targetKeys.value.filter(
          (k: any) => k !== record.key,
        );
      }
    },
    style: { cursor: 'pointer' },
  });
};
</script>

<template>
  <Modal>
    <a-transfer
      :data-source="userRoleDetails"
      :filter-option="
        (inputValue: any, item: any) =>
          item.nickname.includes(inputValue) ||
          item.username.includes(inputValue)
      "
      :show-search="showSearch"
      :show-select-all="false"
      :target-keys="targetKeys"
      @change="onChange"
    >
      <template
        #children="{
          direction,
          filteredItems,
          selectedKeys,
          onItemSelectAll,
          onItemSelect,
        }"
      >
        <a-table
          :columns="direction === 'left' ? leftColumns : rightColumns"
          :custom-row="getCustomRow(direction)"
          :data-source="filteredItems"
          :row-selection="
            getRowSelection({ selectedKeys, onItemSelectAll, onItemSelect })
          "
          size="small"
        />
      </template>
    </a-transfer>
  </Modal>
</template>

<style lang="less">
.m-assign-user {
  width: 75%;
  height: 75%;
}
</style>
