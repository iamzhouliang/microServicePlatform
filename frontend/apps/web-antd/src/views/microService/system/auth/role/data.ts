/**
 * 角色模块测试数据
 * 模拟大数据量场景，每个维度 500 条数据
 */

import type { DimensionDataItem } from './api';

// 生成树形组织架构数据（500个节点）
function generateOrgTree(): DimensionDataItem[] {
  const result: DimensionDataItem[] = [];

  // 生成 10 个一级部门
  for (let i = 1; i <= 10; i++) {
    const level1: DimensionDataItem = {
      id: i,
      name: `集团${i}`,
      children: [],
    };

    // 每个一级部门下 10 个二级部门
    for (let j = 1; j <= 10; j++) {
      const level2: DimensionDataItem = {
        id: i * 100 + j,
        name: `${level1.name}-分公司${j}`,
        children: [],
      };

      // 每个二级部门下 5 个三级部门
      for (let k = 1; k <= 5; k++) {
        const level3: DimensionDataItem = {
          id: i * 10_000 + j * 100 + k,
          name: `${level2.name}-部门${k}`,
        };
        level2.children?.push(level3);
      }

      level1.children?.push(level2);
    }

    result.push(level1);
  }

  return result;
}

// 生成扁平项目数据（500个）
function generateProjects(): DimensionDataItem[] {
  const result: DimensionDataItem[] = [];
  const projectTypes = [
    '基建',
    '房建',
    '市政',
    '水利',
    '交通',
    '能源',
    '环保',
    '通信',
    '装饰',
    '园林',
  ];
  const regions = ['华东', '华南', '华北', '华中', '西南', '西北', '东北'];

  for (let i = 1; i <= 500; i++) {
    const typeIndex = (i - 1) % projectTypes.length;
    const regionIndex = Math.floor((i - 1) / 70) % regions.length;
    result.push({
      id: i,
      name: `${regions[regionIndex]}-${projectTypes[typeIndex]}项目${String(i).padStart(4, '0')}`,
    });
  }

  return result;
}

// 生成扁平客户数据（500个）
function generateCustomers(): DimensionDataItem[] {
  const result: DimensionDataItem[] = [];
  const industries = [
    '制造业',
    '零售业',
    '金融业',
    '互联网',
    '医疗',
    '教育',
    '房地产',
    '物流',
    '餐饮',
    '能源',
  ];
  const scales = ['大型', '中型', '小型'];

  for (let i = 1; i <= 500; i++) {
    const industryIndex = (i - 1) % industries.length;
    const scaleIndex = Math.floor((i - 1) / 170) % scales.length;
    result.push({
      id: i,
      name: `${scales[scaleIndex]}${industries[industryIndex]}客户${String(i).padStart(4, '0')}`,
    });
  }

  return result;
}

// 生成树形网点数据（500个节点）
function generateOutlets(): DimensionDataItem[] {
  const result: DimensionDataItem[] = [];
  const provinces = [
    '北京',
    '上海',
    '广东',
    '浙江',
    '江苏',
    '山东',
    '四川',
    '湖北',
    '河南',
    '福建',
  ];

  // 生成 10 个省级节点
  for (const [i, province_] of provinces.entries()) {
    const province: DimensionDataItem = {
      id: (i + 1) * 1000,
      name: `${province_}省`,
      children: [],
    };

    // 每个省下 10 个市
    for (let j = 1; j <= 10; j++) {
      const city: DimensionDataItem = {
        id: (i + 1) * 1000 + j * 10,
        name: `${province_}${j}市`,
        children: [],
      };

      // 每个市下 5 个网点
      for (let k = 1; k <= 5; k++) {
        const outlet: DimensionDataItem = {
          id: (i + 1) * 1000 + j * 10 + k,
          name: `${city.name}网点${k}`,
        };
        city.children?.push(outlet);
      }

      province.children?.push(city);
    }

    result.push(province);
  }

  return result;
}

// 导出测试数据
export const mockOrgTree = generateOrgTree();
export const mockProjects = generateProjects();
export const mockCustomers = generateCustomers();
export const mockOutlets = generateOutlets();

// 统计数据量
export function getDataStats() {
  const countTree = (items: DimensionDataItem[]): number => {
    return items.reduce((acc, item) => {
      return acc + 1 + (item.children ? countTree(item.children) : 0);
    }, 0);
  };

  return {
    org: countTree(mockOrgTree),
    project: mockProjects.length,
    customer: mockCustomers.length,
    outlet: countTree(mockOutlets),
  };
}
