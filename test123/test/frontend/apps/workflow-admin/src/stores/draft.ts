/**
 * 流程配置草稿 store（内存草稿：编辑期唯一一份数据，点保存才落盘）。
 *
 * 设计：
 * - 向导（图/基本信息）与表单配置页（节点字段）读写同一份内存草稿；
 * - 切节点、切页面都不丢（同一 store）；刷新页面/关闭 tab 丢弃（if rame 销毁）；
 * - 落盘点：向导「保存草稿」/ 表单页「保存配置」/ 发布前自动保存；
 * - store 为纯数据（无定时器/外部监听），无内存泄漏负担。
 */
import { defineStore } from 'pinia';
import * as api from '../api';
import type { NodeFormField, ProcessDef, ProcessLink, ProcessNode } from '../types/schema';

interface DraftBasic {
  defName: string;
  defCode: string;
  description: string;
  initiator: string;
}

interface DraftState {
  /** 流程 id（0 = 新建未保存） */
  defId: number;
  basic: DraftBasic;
  version: number;
  published: boolean;
  /** 流程结构（DAG） */
  nodes: ProcessNode[];
  links: ProcessLink[];
  /** 各节点字段配置（内存草稿；nodeKey -> 字段列表） */
  nodeFields: Record<string, NodeFormField[]>;
  /** 是否已加载（false 时需 loadNew/loadFromServer 初始化） */
  loaded: boolean;
}

/** 自动生成合法流程编码（NCName：字母开头） */
function genDefCode(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `PRC${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
}

export const useDraftStore = defineStore('draft', {
  state: (): DraftState => ({
    defId: 0,
    basic: { defName: '', defCode: genDefCode(), description: '', initiator: 'buyer' },
    version: 0,
    published: false,
    nodes: [],
    links: [],
    nodeFields: {},
    loaded: false,
  }),

  getters: {
    /** 已配置字段的节点 key（fill 节点完成度/流程图角标） */
    configuredKeys(state): string[] {
      return state.nodes
        .filter((n) => n.nodeType === 'fill')
        .filter((n) => (state.nodeFields[n.nodeKey] ?? []).length > 0)
        .map((n) => n.nodeKey);
    },
    /** 是否新建（未保存过） */
    isNew(state): boolean {
      return state.defId === 0;
    },
  },

  actions: {
    /** 初始化新建草稿（清空并生成新编码；向导 mode=new 时调用） */
    initNew(): void {
      this.defId = 0;
      this.basic = { defName: '', defCode: genDefCode(), description: '', initiator: 'buyer' };
      this.version = 0;
      this.published = false;
      this.nodes = [];
      this.links = [];
      this.nodeFields = {};
      this.loaded = true;
    },

    /** 从后端加载已有流程为草稿（图 + 各节点字段；编辑模式/表单页入口时调用） */
    async loadFromServer(defId: number): Promise<void> {
      const def = await api.apiGetDef(defId);
      if (!def) {
        throw new Error('流程不存在');
      }
      this.defId = def.id;
      this.basic = {
        defName: def.defName,
        defCode: def.defCode,
        description: def.description ?? '',
        initiator: def.initiator ?? 'buyer',
      };
      this.version = def.version;
      this.published = def.published;
      this.nodes = def.nodes.map((n) => ({ ...n }));
      this.links = def.links.map((l) => ({ ...l }));
      this.nodeFields = {};
      // 加载各节点字段（start/fill 可配表单；approval/网关不配）
      for (const n of this.nodes) {
        if (n.nodeType === 'start' || n.nodeType === 'fill') {
          this.nodeFields[n.nodeKey] = await api.apiListNodeFields(defId, n.nodeKey);
        }
      }
      this.loaded = true;
    },

    /** 节点字段读写（惰性创建并返回 store 内数组引用：增删改直接落内存草稿） */
    getNodeFields(nodeKey: string): NodeFormField[] {
      if (!this.nodeFields[nodeKey]) {
        this.nodeFields[nodeKey] = [];
      }
      return this.nodeFields[nodeKey];
    },

    setNodeFields(nodeKey: string, fields: NodeFormField[]): void {
      this.nodeFields[nodeKey] = fields;
    },

    /** 构建 ProcessDef（发布/落盘用） */
    buildDef(): ProcessDef {
      return {
        id: this.defId,
        defCode: this.basic.defCode,
        defName: this.basic.defName,
        description: this.basic.description,
        initiator: this.basic.initiator,
        version: this.version,
        published: this.published,
        nodes: this.nodes,
        links: this.links,
      };
    },

    /**
     * 落盘：新建（拿 id）或更新基本信息 + 整图 + 全部节点字段（diff）。
     * @returns 流程 id
     */
    async saveToServer(): Promise<number> {
      let defId = this.defId;
      if (!defId) {
        defId = await api.apiCreateDef({
          code: this.basic.defCode,
          name: this.basic.defName,
          description: this.basic.description,
        });
        this.defId = defId;
      } else {
        await api.apiUpdateDef(defId, { name: this.basic.defName, description: this.basic.description });
      }
      await api.apiSaveGraph(defId, this.nodes, this.links);
      for (const [nodeKey, fields] of Object.entries(this.nodeFields)) {
        await api.apiSaveNodeFields(defId, nodeKey, fields);
      }
      return defId;
    },

    /** 发布（先落盘再发布；后端：版本+1+快照+BPMN 部署） */
    async publish(): Promise<number> {
      const defId = await this.saveToServer();
      return api.apiPublish(defId);
    },
  },
});
