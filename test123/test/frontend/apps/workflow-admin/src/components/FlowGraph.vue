<template>
  <div class="flow-graph">
    <svg
      :width="layout.width"
      :height="layout.height"
      :viewBox="`0 0 ${layout.width} ${layout.height}`"
      class="flow-graph__svg"
    >
      <!-- 边（贝塞尔曲线 + 箭头） -->
      <g v-for="(l, i) in layout.links" :key="`e${i}`">
        <path :d="edgePath(l)" class="edge" />
        <path :d="arrowPath(l)" class="edge-arrow" />
      </g>

      <!-- 排他分支条件标签 -->
      <g
        v-for="(l, i) in layout.links"
        :key="`c${i}`"
        class="cond-label"
        :transform="`translate(${plusPoint(l).x}, ${plusPoint(l).y - 14})`"
      >
        <template v-if="condOf(l)">
          <rect
            :x="-condWidth(condOf(l)) / 2"
            y="-10"
            :width="condWidth(condOf(l))"
            height="20"
            rx="10"
            class="cond-label__bg"
          />
          <text class="cond-label__text" text-anchor="middle" dominant-baseline="central">
            {{ condText(condOf(l)) }}
          </text>
        </template>
      </g>

      <!-- 节点 -->
      <g
        v-for="ln in layout.nodes"
        :key="ln.node.nodeKey"
        class="fg-node"
        :class="{
          'fg-node--selected': ln.node.nodeKey === selectedKey,
          'fg-node--gate': isGate(ln.node.nodeType),
          'fg-node--round': ln.node.nodeType === 'start' || ln.node.nodeType === 'end',
        }"
        :transform="`translate(${ln.x - nodeWidth(ln.node.nodeType) / 2}, ${ln.y - nodeHeight(ln.node.nodeType) / 2})`"
        @click="emit('select', ln.node.nodeKey, $event)"
      >
        <!-- 普通节点：圆角矩形 -->
        <rect
          v-if="!isGate(ln.node.nodeType) && ln.node.nodeType !== 'start' && ln.node.nodeType !== 'end'"
          :width="nodeWidth(ln.node.nodeType)"
          :height="nodeHeight(ln.node.nodeType)"
          rx="10"
          class="node-body"
        />
        <!-- 起止节点：圆形 -->
        <circle
          v-else-if="ln.node.nodeType === 'start' || ln.node.nodeType === 'end'"
          :cx="nodeWidth(ln.node.nodeType) / 2"
          :cy="nodeHeight(ln.node.nodeType) / 2"
          :r="nodeWidth(ln.node.nodeType) / 2"
          class="node-body"
        />
        <!-- 并行网关：菱形 -->
        <path
          v-else
          :d="diamondPath(ln.node.nodeType)"
          class="node-body"
        />

        <template v-if="isGate(ln.node.nodeType)">
          <text :x="nodeWidth(ln.node.nodeType) / 2" :y="nodeHeight(ln.node.nodeType) / 2 - 2" class="node-icon" text-anchor="middle">
            {{ nodeIcon(ln.node.nodeType) }}
          </text>
          <text :x="nodeWidth(ln.node.nodeType) / 2" :y="nodeHeight(ln.node.nodeType) / 2 + 16" class="node-name node-name--gate" text-anchor="middle">
            {{ shortName(ln.node.nodeName, 5) }}
          </text>
        </template>
        <template v-else>
          <text :x="nodeWidth(ln.node.nodeType) / 2" :y="nodeHeight(ln.node.nodeType) / 2 - 10" class="node-icon" text-anchor="middle">
            {{ nodeIcon(ln.node.nodeType) }}
          </text>
          <text :x="nodeWidth(ln.node.nodeType) / 2" :y="nodeHeight(ln.node.nodeType) / 2 + 16" class="node-name" text-anchor="middle">
            {{ shortName(ln.node.nodeName, 10) }}
          </text>
          <text :x="nodeWidth(ln.node.nodeType) / 2" :y="nodeHeight(ln.node.nodeType) / 2 + 32" class="node-type" text-anchor="middle">
            {{ typeText(ln.node.nodeType) }}{{ rejectSuffix(ln.node) }}
          </text>
        </template>

        <!-- fill 节点未配置字段角标 -->
        <circle
          v-if="ln.node.nodeType === 'fill' && !isConfigured(ln.node.nodeKey)"
          :cx="nodeWidth(ln.node.nodeType) - 14"
          :cy="14"
          r="6"
          class="node-badge"
        />
      </g>

      <!-- 连线中点「+」按钮（编辑模式） -->
      <g
        v-if="editable"
        v-for="(l, i) in layout.links"
        :key="`p${i}`"
        class="fg-plus"
        :transform="`translate(${plusPoint(l).x}, ${plusPoint(l).y})`"
        @click.stop="emit('addAt', l.fromKey, l.toKey, $event)"
      >
        <circle r="16" class="fg-plus__bg" />
        <text class="fg-plus__text" text-anchor="middle" dominant-baseline="central">+</text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
/**
 * 流程图画布（自绘 SVG DAG）
 * 说明：
 * 1. 支持线性链与并行分叉/汇合（parallelSplit/parallelJoin 网关渲染为菱形）；
 * 2. editable 模式下在每条连线中点渲染「+」按钮（插入节点/并行分支入口）；
 * 3. 颜色全部走 CSS 变量，自动适配浅色/深色主题。
 * 向导第 3 步（编辑）、表单配置页（点选）、向导第 5 步（预览）共用。
 */
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { layoutGraph, nodeWidth, type LaidOutLink } from '../utils/graphLayout';
import type { BranchCondition, NodeType, ProcessLink, ProcessNode } from '../types/schema';

const props = defineProps<{
  /** 节点集合（DAG） */
  nodes: ProcessNode[];
  /** 连线集合 */
  links: ProcessLink[];
  /** 当前选中节点 key */
  selectedKey: string | null;
  /** 已配置字段的节点 key 集合 */
  configuredKeys?: string[];
  /** 编辑模式：显示连线中点「+」按钮 */
  editable?: boolean;
}>();

const emit = defineEmits<{
  (e: 'select', nodeKey: string, event: MouseEvent): void;
  (e: 'addAt', fromKey: string, toKey: string, event: MouseEvent): void;
}>();

const { t } = useI18n();

const layout = computed(() => layoutGraph(props.nodes, props.links));

/** 节点高度（与 graphLayout 尺寸约定一致） */
function nodeHeight(type: NodeType): number {
  if (
    type === 'parallelSplit' ||
    type === 'parallelJoin' ||
    type === 'exclusiveSplit' ||
    type === 'exclusiveJoin'
  ) {
    return 56;
  }
  if (type === 'start' || type === 'end') return 68;
  return 76;
}

function isGate(type: NodeType): boolean {
  return (
    type === 'parallelSplit' ||
    type === 'parallelJoin' ||
    type === 'exclusiveSplit' ||
    type === 'exclusiveJoin'
  );
}

function nodeIcon(type: NodeType): string {
  const map: Record<NodeType, string> = {
    start: '▶',
    fill: '✏️',
    approval: '✔️',
    parallelSplit: '⇉',
    parallelJoin: '⇇',
    exclusiveSplit: '×',
    exclusiveJoin: '×',
    end: '⏹',
  };
  return map[type];
}

function typeText(type: NodeType): string {
  const map: Record<NodeType, string> = {
    start: 'wizard.nodeTypeStart',
    fill: 'wizard.nodeTypeFill',
    approval: 'wizard.nodeTypeApproval',
    parallelSplit: 'wizard.nodeTypeSplit',
    parallelJoin: 'wizard.nodeTypeJoin',
    exclusiveSplit: 'wizard.nodeTypeExSplit',
    exclusiveJoin: 'wizard.nodeTypeExJoin',
    end: 'wizard.nodeTypeEnd',
  };
  return t(map[type]);
}

/** 审批节点驳回策略后缀（画布徽标） */
function rejectSuffix(node: ProcessNode): string {
  if (node.nodeType !== 'approval' || !node.rejectStrategy || node.rejectStrategy === 'back_prev') return '';
  const map: Record<string, string> = {
    back_starter: 'wizard.rejectStarterShort',
    terminate: 'wizard.rejectTerminateShort',
  };
  return ` · ${t(map[node.rejectStrategy] ?? 'wizard.rejectTerminateShort')}`;
}

/** 某条边上的条件（来自原始 links） */
function condOf(l: LaidOutLink): BranchCondition | undefined {
  return props.links.find((x) => x.fromKey === l.fromKey && x.toKey === l.toKey)?.condition;
}

/** 条件标签文案 */
function condText(c: BranchCondition | undefined): string {
  if (!c) return '';
  if (c.isDefault) return t('wizard.condDefault');
  const opMap: Record<string, string> = {
    gt: '>',
    gte: '≥',
    lt: '<',
    lte: '≤',
    eq: '=',
    neq: '≠',
    contains: t('wizard.condContains'),
  };
  return `${c.fieldName} ${opMap[c.operator] ?? c.operator} ${c.value}`;
}

/** 条件标签宽度估算（中文 12px / 其他 7px） */
function condWidth(c: BranchCondition | undefined): number {
  const text = condText(c);
  let w = 0;
  for (const ch of text) w += /[\u4e00-\u9fa5]/.test(ch) ? 12 : 7;
  return Math.min(Math.max(w + 16, 36), 170);
}

function shortName(name: string, max: number): string {
  return name.length > max ? `${name.slice(0, max)}…` : name;
}

function isConfigured(nodeKey: string): boolean {
  if (!props.configuredKeys) return true;
  return props.configuredKeys.includes(nodeKey);
}

function diamondPath(type: NodeType): string {
  const w = nodeWidth(type);
  const h = nodeHeight(type);
  return `M ${w / 2} 0 L ${w} ${h / 2} L ${w / 2} ${h} L 0 ${h / 2} Z`;
}

function findPos(key: string): { x: number; y: number; w: number } {
  const ln = layout.value.nodes.find((n) => n.node.nodeKey === key);
  if (!ln) return { x: 0, y: 0, w: 0 };
  return { x: ln.x, y: ln.y, w: nodeWidth(ln.node.nodeType) };
}

/** 边路径：水平贝塞尔（相邻层之间） */
function edgePath(l: LaidOutLink): string {
  const from = findPos(l.fromKey);
  const to = findPos(l.toKey);
  const x1 = from.x + from.w / 2;
  const y1 = from.y;
  const x2 = to.x - to.w / 2;
  const y2 = to.y;
  const mx = (x1 + x2) / 2;
  return `M ${x1} ${y1} C ${mx} ${y1}, ${mx} ${y2}, ${x2} ${y2}`;
}

/** 箭头（指向目标节点左缘） */
function arrowPath(l: LaidOutLink): string {
  const to = findPos(l.toKey);
  const x2 = to.x - to.w / 2;
  const y2 = to.y;
  const s = 6;
  return `M ${x2} ${y2} L ${x2 - 12} ${y2 - s} L ${x2 - 12} ${y2 + s} Z`;
}

/** 「+」按钮位置（边中点） */
function plusPoint(l: LaidOutLink): { x: number; y: number } {
  const from = findPos(l.fromKey);
  const to = findPos(l.toKey);
  const x1 = from.x + from.w / 2;
  const x2 = to.x - to.w / 2;
  return { x: (x1 + x2) / 2, y: (from.y + to.y) / 2 };
}
</script>

<style scoped>
.flow-graph {
  overflow-x: auto;
  padding: 8px 0;
}
.flow-graph__svg {
  display: block;
}
.edge {
  fill: none;
  stroke: var(--brand-border-strong);
  stroke-width: 1.5;
}
.edge-arrow {
  fill: var(--brand-border-strong);
}
.node-body {
  fill: var(--brand-bg-secondary);
  stroke: var(--brand-border-strong);
  stroke-width: 1.5;
  transition: fill 0.15s ease, stroke 0.15s ease;
}
.fg-node {
  cursor: pointer;
}
.fg-node:hover .node-body {
  fill: var(--brand-bg-tertiary);
}
.fg-node--selected .node-body {
  fill: color-mix(in srgb, var(--brand-primary) 10%, var(--brand-bg));
  stroke: var(--brand-primary);
  stroke-width: 2.5;
}
.node-icon {
  font-size: 17px;
}
.node-name {
  font-size: 13px;
  font-weight: 600;
  fill: var(--brand-text);
}
.node-name--gate {
  font-size: 11px;
}
.node-type {
  font-size: 11px;
  fill: var(--brand-text-secondary);
}
.node-badge {
  fill: var(--brand-warning);
}
.cond-label__bg {
  fill: var(--brand-bg);
  stroke: var(--brand-border-strong);
  stroke-width: 1;
}
.cond-label__text {
  font-size: 11px;
  fill: var(--brand-text-secondary);
}
.fg-plus__bg {
  fill: var(--brand-primary);
  stroke: var(--brand-primary-contrast);
  stroke-width: 1;
  transition: fill 0.15s ease;
}
.fg-plus__text {
  fill: var(--brand-primary-contrast);
  font-size: 16px;
  font-weight: 700;
  pointer-events: none;
}
.fg-plus {
  cursor: pointer;
}
.fg-plus:hover .fg-plus__bg {
  fill: var(--brand-primary-hover);
}
</style>
