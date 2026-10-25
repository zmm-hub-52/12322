import type { ApiRuleContext, ExternalApiRule, HttpInvocation } from './types';
import { ParamResolver } from './ParamResolver';

/**
 * HttpInvocationBuilder：把 ExternalApiRule 对象 + 上下文编译成 HttpInvocation。
 * URL 用标准 URL 对象组装 query，不在业务 service 里手工拼 '?a=1&b=2'。
 */
export class HttpInvocationBuilder {
  private readonly resolver = new ParamResolver();

  build(rule: ExternalApiRule, ctx: ApiRuleContext): HttpInvocation {
    const headers: Record<string, string> = {};
    for (const b of rule.request.headers) {
      headers[b.targetPath] = String(this.resolver.resolve(b, ctx) ?? '');
    }
    this.applyAuth(rule, headers);

    const url = new URL(rule.endpoint.url);
    const query: Record<string, string> = {};
    for (const b of rule.request.queryParams) {
      const value = String(this.resolver.resolve(b, ctx) ?? '');
      url.searchParams.set(b.targetPath, value);
      query[b.targetPath] = value;
    }

    return {
      method: rule.endpoint.method,
      url: url.toString(),
      headers,
      query,
      body: this.buildBody(rule, ctx),
    };
  }

  private buildBody(rule: ExternalApiRule, ctx: ApiRuleContext): unknown {
    const tpl = rule.request.body;
    if (tpl.type === 'raw') return tpl.rawTemplate ?? null;
    const body: Record<string, unknown> = {};
    for (const b of tpl.fields ?? []) {
      body[b.targetPath] = this.resolver.resolve(b, ctx);
    }
    return body;
  }

  private applyAuth(rule: ExternalApiRule, headers: Record<string, string>): void {
    const auth = rule.endpoint.auth;
    switch (auth.type) {
      case 'basic':
        headers.Authorization = 'Basic ' + btoa(String(auth.username ?? '') + ':' + String(auth.password ?? ''));
        break;
      case 'bearer':
        headers.Authorization = 'Bearer ' + (auth.token ?? '');
        break;
      case 'header':
        if (auth.headerName) headers[auth.headerName] = auth.headerValue ?? '';
        break;
      case 'none':
      case 'oauth2':
      default:
        break;
    }
  }
}
