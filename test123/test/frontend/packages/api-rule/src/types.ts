import type { ExprNode } from '@platform/rule-engine';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
export type AuthType = 'none' | 'basic' | 'bearer' | 'header' | 'oauth2';
export type ParamSourceType = 'constant' | 'field' | 'context';
export type FailStrategy = 'skip' | 'fail' | 'manual';

export interface ApiAuth {
  type: AuthType;
  username?: string;
  password?: string;
  token?: string;
  headerName?: string;
  headerValue?: string;
}

export interface ApiEndpoint {
  method: HttpMethod;
  url: string;
  auth: ApiAuth;
}

export interface ParamBinding {
  sourceType: ParamSourceType;
  sourceRef: string;
  targetPath: string;
}

export interface BodyTemplate {
  type: 'json' | 'form' | 'raw';
  fields?: ParamBinding[];
  rawTemplate?: string;
}

export interface ApiRequestTemplate {
  headers: ParamBinding[];
  queryParams: ParamBinding[];
  body: BodyTemplate;
}

export interface FieldMapping {
  sourcePath: string;
  targetFieldKey: string;
}

export interface ApiResponseMapping {
  successPath: string;
  codePath: string;
  messagePath: string;
  dataPath: string;
  fieldMappings: FieldMapping[];
}

export interface ApiInvokePolicy {
  retryCount: number;
  timeoutMs: number;
  failStrategy: FailStrategy;
}

/** 外部 API 规则：一个完整对象，不落成散落字符串。 */
export interface ExternalApiRule {
  id: string;
  name: string;
  enabled: boolean;
  trigger?: ExprNode;
  endpoint: ApiEndpoint;
  request: ApiRequestTemplate;
  response: ApiResponseMapping;
  invokePolicy: ApiInvokePolicy;
}

export interface ApiRuleContext {
  fields: Record<string, unknown>;
  context?: Record<string, unknown>;
}

/** 编译产物：一个结构化的 HTTP 调用对象，交给 HTTP 客户端执行。 */
export interface HttpInvocation {
  method: HttpMethod;
  url: string;
  headers: Record<string, string>;
  query: Record<string, string>;
  body?: unknown;
}
