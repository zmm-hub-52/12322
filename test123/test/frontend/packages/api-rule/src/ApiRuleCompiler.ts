import type { ApiRuleContext, ExternalApiRule, HttpInvocation } from './types';
import { HttpInvocationBuilder } from './HttpInvocationBuilder';

/**
 * ApiRuleCompiler：编译 ExternalApiRule 为可执行函数。
 * 调用方只拿到 (ctx) => HttpInvocation，不接触 URL/header/body 拼接细节。
 */
export class ApiRuleCompiler {
  private readonly builder = new HttpInvocationBuilder();

  compile(rule: ExternalApiRule): (ctx: ApiRuleContext) => HttpInvocation {
    return (ctx) => this.builder.build(rule, ctx);
  }
}
