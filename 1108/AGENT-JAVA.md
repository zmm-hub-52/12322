# AGENT-JAVA.md — AI 编码规范（Java · Spring Boot 3）

> 目标：让 AI 产出的代码结构清晰、风格统一、**不重复造轮子**、可直接上线。
> 技术栈：Spring Boot 3.x · MyBatis-Plus · Lombok · Hutool · Maven · JDK 17+
> 分级：【必须】硬性遵守 ·【推荐】尽量遵守 ·【参考】可选

## 一、总原则（最高优先级）

【必须】禁止造轮子。写任何工具方法/通用逻辑前，先确认 JDK、Spring、Hutool、Lombok、MyBatis-Plus 是否已提供；已提供则直接调用，禁止手写等价实现。
【必须】优先标准库：日期用 java.time，集合/流用 Stream，判空用 Optional/Objects，JSON 用 Jackson，工具用 Hutool，分页/CRUD 用 MyBatis-Plus。
【必须】先读后写：改代码前先理解现有结构与约定，沿用既有模式，不另起炉灶。
【必须】最小改动：只改需求范围，不顺手重构、不动无关代码、不引入无关依赖。
【必须】新增第三方依赖前，先确认项目是否已有等价依赖；确需引入必须说明理由。

## 二、工程结构

【必须】严格分层，依赖单向：controller → service → mapper；禁止跨层调用、禁止 controller 直连 mapper、禁止循环依赖。
【必须】目录约定：

    controller/  接口层：参数校验、组装返回，不写业务
    service/     业务层：事务与业务编排（+ impl/）
    mapper/      持久层：MyBatis-Plus Mapper
    entity/      表实体
    dto/         入参/出参对象
    vo/          视图对象
    config/      配置类
    enums/       枚举
    exception/   全局异常
    util/        通用工具（仅放业务无关、且现有库不提供的）

【必须】DTO/VO/Entity 分离：禁止用 Entity 直接接参或返回前端，必须转换。
【推荐】Service 定义接口 + impl 实现。

## 三、命名规约

【必须】类名 UpperCamelCase；方法/变量/参数 lowerCamelCase；常量 UPPER_SNAKE_CASE。
【必须】见名知意，禁止 a、b、temp、data、obj 等无意义命名。
【必须】Boolean 属性用包装类 Boolean，方法名不加 is 前缀（isDeleted → deleted）。
【必须】方法动词开头：get/list/create/save/update/delete。
【必须】接口不叫 IXxxService，实现类统一 XxxServiceImpl。
【推荐】包名全小写单数。

## 四、编码习惯

【必须】异常：禁止空 catch、禁止只 printStackTrace、禁止用异常控制流程。
【必须】全局异常统一处理（@RestControllerAdvice）；业务异常抛自定义 BusinessException（带错误码），禁止到处 try-catch。
【必须】日志：用 @Slf4j，禁止 System.out/printStackTrace；异常日志必须含关键入参 + 错误信息 + 堆栈；按场景选级别（debug/info/warn/error）。
【必须】空值：POJO 属性用包装类型；集合/对象使用前判空；对外接口不返回 null（返回空集合/空对象/默认值）。
【必须】常量：魔法值（字符串/数字）禁止散落代码，定义为常量或枚举。
【必须】集合：判空用 isEmpty()；遍历 Map 用 entrySet；勿在 foreach 中增删元素。
【必须】并发：线程池禁止用 Executors 创建，用 ThreadPoolTaskExecutor 显式配置参数。
【必须】SQL：MyBatis 用 #{ } 占位符，禁止 ${ } 拼接用户输入（防 SQL 注入）。
【必须】事务：@Transactional 加在 service 方法；规避失效场景（同类自调用、非 public、异常被吞）。
【推荐】用 Lombok 减样板；实体避免 @Data（用 @Getter/@Setter），避免 equals/hashCode 副作用。

## 五、禁止事项（AI 高频翻车点）

【禁止】重复造轮子（见总原则第一条）。
【禁止】用 Entity 当 DTO/VO 直接传输。
【禁止】空 catch、吞异常、返回 null 假成功。
【禁止】System.out 打日志。
【禁止】魔法值散落代码。
【禁止】controller 里写业务逻辑。
【禁止】手写分页/日期/JSON/加解密/校验等通用逻辑（用 MyBatis-Plus Page / java.time / Jackson / Hutool / validation）。
【禁止】无注释的复杂方法；改动后不更新注释。
【禁止】未确认就引入新第三方依赖。
【禁止】复制粘贴同类代码不抽取公共方法（三处重复必须抽取）。
