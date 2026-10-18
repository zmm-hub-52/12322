/**
 * DAG 分层布局算法（Sugiyama 简化版）
 * 说明：
 * 1. Kahn 拓扑排序求每个节点的「最长路径深度」（保证并行汇合在最长分支之后）；
 * 2. 同层节点按「前驱平均序号」barycenter 排序，减少连线交叉；
 * 3. 输出每个节点的中心坐标 + 画布宽高，供 SVG 渲染。
 */
import type { ProcessLink, ProcessNode } from '../types/schema';

export interface LaidOutNode {
  node: ProcessNode;
  /** 节点中心 x */
  x: number;
  /** 节点中心 y */
  y: number;
  /** 层号（start=0） */
  depth: number;
}

export interface LaidOutLink {
  fromKey: string;
  toKey: string;
}

export interface GraphLayout {
  nodes: LaidOutNode[];
  links: LaidOutLink[];
  width: number;
  height: number;
}

/** 节点绘制尺寸（与 FlowGraph 组件保持一致） */
export const NODE_W = 150;
export const NODE_H = 76;
/** 网关（并行分叉/汇合）菱形尺寸 */
export const GATE_W = 64;
export const GATE_H = 56;
/** 起止节点圆直径 */
export const ROUND_D = 68;
const GAP_X = 84;
const GAP_Y = 110;

/** 节点占位宽度（按类型） */
export function nodeWidth(type: ProcessNode['nodeType']): number {
  if (
    type === 'parallelSplit' ||
    type === 'parallelJoin' ||
    type === 'exclusiveSplit' ||
    type === 'exclusiveJoin'
  ) {
    return GATE_W;
  }
  if (type === 'start' || type === 'end') return ROUND_D;
  return NODE_W;
}

export function layoutGraph(nodes: ProcessNode[], links: ProcessLink[]): GraphLayout {
  // 邻接表 + 入度
  const adj = new Map<string, string[]>();
  const indeg = new Map<string, number>();
  nodes.forEach((n) => {
    adj.set(n.nodeKey, []);
    indeg.set(n.nodeKey, 0);
  });
  links.forEach((l) => {
    if (!adj.has(l.fromKey) || !adj.has(l.toKey)) return;
    adj.get(l.fromKey)!.push(l.toKey);
    indeg.set(l.toKey, indeg.get(l.toKey)! + 1);
  });

  // Kahn 拓扑：深度 = 最长前驱路径 + 1
  const depth = new Map<string, number>();
  const queue: string[] = [];
  nodes.forEach((n) => {
    if (indeg.get(n.nodeKey) === 0) {
      queue.push(n.nodeKey);
      depth.set(n.nodeKey, 0);
    }
  });
  let qi = 0;
  while (qi < queue.length) {
    const cur = queue[qi++];
    for (const next of adj.get(cur) ?? []) {
      const nd = (depth.get(cur) ?? 0) + 1;
      if (nd > (depth.get(next) ?? -1)) depth.set(next, nd);
      indeg.set(next, indeg.get(next)! - 1);
      if (indeg.get(next) === 0) queue.push(next);
    }
  }

  // 按层分组
  const groups = new Map<number, ProcessNode[]>();
  nodes.forEach((n) => {
    const d = depth.get(n.nodeKey) ?? 0;
    const arr = groups.get(d) ?? [];
    arr.push(n);
    groups.set(d, arr);
  });
  const maxDepth = Math.max(...groups.keys(), 0);

  // 坐标：x 按层；y 按「前驱 y 均值」对齐所属支线（而非层内简单居中），
  // 逐节点放置并防止重叠（prevY + GAP_Y），保证：
  // - B 支线新插节点与 B 保持同一水平线，不会被居中漂移到 A 的位置；
  // - 同层多节点按 desiredY 排序，支线互不交叉。
  const laid: LaidOutNode[] = [];
  const yOf = new Map<string, number>();
  let maxY = 0;

  for (let d = 0; d <= maxDepth; d++) {
    const layer = groups.get(d) ?? [];
    const x = d * (NODE_W + GAP_X) + NODE_W / 2;

    const items: { n: ProcessNode; desired: number; y?: number }[] = layer.map((n, i) => {
      const preds = links.filter((l) => l.toKey === n.nodeKey);
      const ys = preds.map((l) => yOf.get(l.fromKey) ?? 0);
      // 无前驱（仅 start 层）：按层内序号给初始位置
      const desired = ys.length ? ys.reduce((s, y) => s + y, 0) / ys.length : i * GAP_Y + 50;
      return { n, desired };
    });
    items.sort((a, b) => a.desired - b.desired);

    let prevY = Number.NEGATIVE_INFINITY;
    for (const it of items) {
      const y = Math.max(it.desired, prevY + GAP_Y);
      it.y = y;
      prevY = y;
    }
    for (const it of items) {
      yOf.set(it.n.nodeKey, it.y!);
      maxY = Math.max(maxY, it.y!);
      laid.push({ node: it.n, x, y: it.y!, depth: d });
    }
  }

  return {
    nodes: laid,
    links: links.map((l) => ({ fromKey: l.fromKey, toKey: l.toKey })),
    width: (maxDepth + 1) * (NODE_W + GAP_X) - GAP_X + 32,
    height: maxY + 150,
  };
}
