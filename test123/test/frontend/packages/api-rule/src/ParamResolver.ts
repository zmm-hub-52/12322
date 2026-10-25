import type { ApiRuleContext, ParamBinding } from './types';

/**
 * 参数取值器：把 ParamBinding 对象解析为具体值。
 * constant 直接返回；field/context 从上下文中取。
 */
export class ParamResolver {
  resolve(binding: ParamBinding, ctx: ApiRuleContext): unknown {
    switch (binding.sourceType) {
      case 'constant':
        return binding.sourceRef;
      case 'field':
        return ctx.fields[binding.sourceRef];
      case 'context':
        return ctx.context?.[binding.sourceRef];
      default:
        return undefined;
    }
  }
}
