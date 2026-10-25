/**
 * 流程配置草稿 store（内存草稿：编辑期唯一一份数据，点保存才落盘）。
 * 纯表格模型：nodeFields 即节点表格列配置。
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
  defId: number;
  basic: DraftBasic;
  version: number;
  published: boolean;
  nodes: ProcessNode[];
  links: ProcessLink[];
  nodeFields: Record<string, NodeFormField[]>;
  loaded: boolean;
}

function genDefCode(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return 'PRC' + d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate()) + pad(d.getHours()) + pad(d.getMinutes()) + pad(d.getSeconds());
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
    configuredKeys(state): string[] {
      return state.nodes
        .filter((n) => n.nodeType === 'start' || n.nodeType === 'fill' || n.nodeType === 'approval')
        .filter((n) => (state.nodeFields[n.nodeKey] ?? []).length > 0)
        .map((n) => n.nodeKey);
    },
    isNew(state): boolean {
      return state.defId === 0;
    },
  },

  actions: {
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
      for (const n of this.nodes) {
        if (n.nodeType === 'start' || n.nodeType === 'fill' || n.nodeType === 'approval') {
          this.nodeFields[n.nodeKey] = await api.apiListNodeFields(defId, n.nodeKey);
        }
      }
      this.loaded = true;
    },

    getNodeFields(nodeKey: string): NodeFormField[] {
      if (!this.nodeFields[nodeKey]) {
        this.nodeFields[nodeKey] = [];
      }
      return this.nodeFields[nodeKey];
    },

    setNodeFields(nodeKey: string, fields: NodeFormField[]): void {
      this.nodeFields[nodeKey] = fields;
    },

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

    async publish(): Promise<number> {
      const defId = await this.saveToServer();
      return api.apiPublish(defId);
    },
  },
});
