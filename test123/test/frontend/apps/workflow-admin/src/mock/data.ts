/**
 * workflow-admin 本地 mock 数据（v3 精简：仅保留流程模式模板）。
 * 其余数据（流程/维表/字段/节点字段）全部来自后端 workflow-service。
 */

import type { ProcessTemplate } from '../types/schema';

// ==================== 流程模式模板 ====================

export const MOCK_TEMPLATES: ProcessTemplate[] = [
  {
    key: 'simple',
    name: '单级审批',
    description: '发起 → 填写 → 审批 → 结束（最常用）',
    nodes: [
      { nodeKey: 'start', nodeName: '开始', nodeType: 'start' },
      { nodeKey: 'fill_1', nodeName: '业务填写', nodeType: 'fill' },
      { nodeKey: 'approve_1', nodeName: '审批', nodeType: 'approval' },
      { nodeKey: 'end', nodeName: '结束', nodeType: 'end' },
    ],
    links: [
      { id: 1, fromKey: 'start', toKey: 'fill_1' },
      { id: 2, fromKey: 'fill_1', toKey: 'approve_1' },
      { id: 3, fromKey: 'approve_1', toKey: 'end' },
    ],
  },
  {
    key: 'double',
    name: '双级审批',
    description: '发起 → 填写 → 审批 → 审批 → 结束（两级把关）',
    nodes: [
      { nodeKey: 'start', nodeName: '开始', nodeType: 'start' },
      { nodeKey: 'fill_1', nodeName: '业务填写', nodeType: 'fill' },
      { nodeKey: 'approve_1', nodeName: '一级审批', nodeType: 'approval' },
      { nodeKey: 'approve_2', nodeName: '二级审批', nodeType: 'approval' },
      { nodeKey: 'end', nodeName: '结束', nodeType: 'end' },
    ],
    links: [
      { id: 1, fromKey: 'start', toKey: 'fill_1' },
      { id: 2, fromKey: 'fill_1', toKey: 'approve_1' },
      { id: 3, fromKey: 'approve_1', toKey: 'approve_2' },
      { id: 4, fromKey: 'approve_2', toKey: 'end' },
    ],
  },
  {
    key: 'parallel',
    name: '并行会签',
    description: '发起 → 填写 → 双线并行审批 → 汇合 → 结束（两条线同时审批）',
    nodes: [
      { nodeKey: 'start', nodeName: '开始', nodeType: 'start' },
      { nodeKey: 'fill_1', nodeName: '业务填写', nodeType: 'fill' },
      { nodeKey: 'split_1', nodeName: '并行分叉', nodeType: 'parallelSplit' },
      { nodeKey: 'approve_a', nodeName: '并行审批 A', nodeType: 'approval' },
      { nodeKey: 'approve_b', nodeName: '并行审批 B', nodeType: 'approval' },
      { nodeKey: 'join_1', nodeName: '并行汇合', nodeType: 'parallelJoin' },
      { nodeKey: 'end', nodeName: '结束', nodeType: 'end' },
    ],
    links: [
      { id: 1, fromKey: 'start', toKey: 'fill_1' },
      { id: 2, fromKey: 'fill_1', toKey: 'split_1' },
      { id: 3, fromKey: 'split_1', toKey: 'approve_a' },
      { id: 4, fromKey: 'split_1', toKey: 'approve_b' },
      { id: 5, fromKey: 'approve_a', toKey: 'join_1' },
      { id: 6, fromKey: 'approve_b', toKey: 'join_1' },
      { id: 7, fromKey: 'join_1', toKey: 'end' },
    ],
  },
  {
    key: 'exclusive',
    name: '条件分流',
    description: '发起 → 填写 → 按条件分流（满足走 A，否则走 B）→ 汇合 → 结束',
    nodes: [
      { nodeKey: 'start', nodeName: '开始', nodeType: 'start' },
      { nodeKey: 'fill_1', nodeName: '业务填写', nodeType: 'fill' },
      { nodeKey: 'xsplit_1', nodeName: '排他分叉', nodeType: 'exclusiveSplit' },
      { nodeKey: 'approve_a', nodeName: '条件审批 A', nodeType: 'approval' },
      { nodeKey: 'approve_b', nodeName: '条件审批 B（默认）', nodeType: 'approval' },
      { nodeKey: 'xjoin_1', nodeName: '排他汇合', nodeType: 'exclusiveJoin' },
      { nodeKey: 'end', nodeName: '结束', nodeType: 'end' },
    ],
    links: [
      { id: 1, fromKey: 'start', toKey: 'fill_1' },
      { id: 2, fromKey: 'fill_1', toKey: 'xsplit_1' },
      {
        id: 3,
        fromKey: 'xsplit_1',
        toKey: 'approve_a',
        condition: { fieldKey: 'qty', fieldName: '数量', operator: 'gt', value: '100' },
      },
      {
        id: 4,
        fromKey: 'xsplit_1',
        toKey: 'approve_b',
        condition: { fieldKey: '', fieldName: '', operator: '', value: '', isDefault: true },
      },
      { id: 5, fromKey: 'approve_a', toKey: 'xjoin_1' },
      { id: 6, fromKey: 'approve_b', toKey: 'xjoin_1' },
      { id: 7, fromKey: 'xjoin_1', toKey: 'end' },
    ],
  },
  {
    key: 'multi-fill',
    name: '多段填写',
    description: '发起 → 填写 → 填写 → 审批 → 结束（分段录入）',
    nodes: [
      { nodeKey: 'start', nodeName: '开始', nodeType: 'start' },
      { nodeKey: 'fill_1', nodeName: '第一阶段填写', nodeType: 'fill' },
      { nodeKey: 'fill_2', nodeName: '第二阶段填写', nodeType: 'fill' },
      { nodeKey: 'approve_1', nodeName: '审批', nodeType: 'approval' },
      { nodeKey: 'end', nodeName: '结束', nodeType: 'end' },
    ],
    links: [
      { id: 1, fromKey: 'start', toKey: 'fill_1' },
      { id: 2, fromKey: 'fill_1', toKey: 'fill_2' },
      { id: 3, fromKey: 'fill_2', toKey: 'approve_1' },
      { id: 4, fromKey: 'approve_1', toKey: 'end' },
    ],
  },
];
