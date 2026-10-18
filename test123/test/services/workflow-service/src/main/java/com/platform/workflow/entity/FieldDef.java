package com.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.workflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字段定义（v3：挂在维度分类下——维表第三层就是字段）。
 * 跨流程复用：节点配置表单时按 field_key 引用；字段上维护「默认规则」
 * （取值规则/校验/必填），节点级可覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("field_def")
public class FieldDef extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属维度分类（维表层级：维度 → 分类 → 字段） */
    private Long categoryId;

    /** 字段 key（全局唯一，业务语义标识：语义不同必须不同 key） */
    private String fieldKey;

    /** 字段名（默认 label） */
    private String fieldName;

    /**
     * 中立业务类型（不存组件名，前端映射到 iX 组件）：
     * input/textarea/number/select/date/boolean
     */
    private String fieldType;

    /**
     * JSON：默认取值规则（防呆核心）
     * - {type:'enum', options:['A','B','C']}  枚举（选项由运维手填）
     * - {type:'codeRange', prefix:'A', start:1, end:14, width:2} 编码范围（自动生成连续编号）
     * - {type:'range', min:1, max:100}       数值范围
     */
    private String valueRule;

    /** JSON：默认校验 {maxLength, pattern, ...}（管「值长什么样」，与取值规则分开） */
    private String validation;

    /** 默认是否必填（节点可覆盖） */
    private Boolean required;

    /** 排序 */
    private Integer sortNo;

    /** 软删除（防呆：不物理删，节点引用不失效） */
    @TableLogic
    private Boolean enabled;
}
