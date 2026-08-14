package com.atguigu.java.ai.langchain4j.workflow.config;

import com.atguigu.java.ai.langchain4j.assistant.XiaozhiAgent;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.store.MongoChatMemoryStore;
import com.atguigu.java.ai.langchain4j.workflow.nodes.BookAppointmentNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.CancelAppointmentNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.ConfirmValidateNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.IntentClassifyNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.QueryAvailabilityNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.ResponseAssembleNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.SlotCollectNode;
import com.atguigu.java.ai.langchain4j.workflow.router.IntentRouter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流 Bean 装配配置类。
 * 所有 Node 用构造函数注入，集中在这里 new 出来（避免 Node 污染 @Autowired 字段注入，
 * 保证 AC-9 的纯函数可 Mock 可单测要求）。
 */
@Configuration
public class XiaozhiWorkflowConfig {

    /**
     * 意图分类节点：非流式 ChatLanguageModel（分类任务一次返回 JSON，不需要 streaming）。
     * 注意：这里复用 qwenChatModel（如果存在的话）或 LangChain4j 自动装配的默认 ChatLanguageModel。
     * 如果项目只有 streamingChatModel，也可以用 streaming 版转一次性 response；这里用通用接口更安全。
     */
    @Bean
    public IntentClassifyNode intentClassifyNode(ChatLanguageModel chatLanguageModel) {
        return new IntentClassifyNode(chatLanguageModel);
    }

    @Bean
    public SlotCollectNode slotCollectNode() {
        return new SlotCollectNode();
    }

    @Bean
    public ConfirmValidateNode confirmValidateNode() {
        return new ConfirmValidateNode();
    }

    @Bean
    public QueryAvailabilityNode queryAvailabilityNode(AppointmentService appointmentService) {
        return new QueryAvailabilityNode(appointmentService);
    }

    @Bean
    public BookAppointmentNode bookAppointmentNode(AppointmentService appointmentService) {
        return new BookAppointmentNode(appointmentService);
    }

    @Bean
    public CancelAppointmentNode cancelAppointmentNode(AppointmentService appointmentService) {
        return new CancelAppointmentNode(appointmentService);
    }

    @Bean
    public ResponseAssembleNode responseAssembleNode() {
        return new ResponseAssembleNode();
    }

    @Bean
    public IntentRouter intentRouter() {
        return new IntentRouter();
    }

    /**
     * 总编排服务（Task 6 实现类）：组合所有 Node + Router + Memory + Agent(Fallback)
     *
     * XiaozhiAgent 是由 LangChain4j 的 @AiService 注解通过 Spring Boot Starter 自动生成的
     * JDK Proxy Bean，beanName 默认为「接口名首字母小写」即 xiaozhiAgent。这里不加
     * @Qualifier 也能正确按类型注入，保险起见写清楚名称，避免容器内同类型多实例歧义。
     */
    @Bean
    public com.atguigu.java.ai.langchain4j.workflow.service.XiaozhiWorkflowService xiaozhiWorkflowService(
            IntentClassifyNode intentClassifyNode,
            SlotCollectNode slotCollectNode,
            ConfirmValidateNode confirmValidateNode,
            QueryAvailabilityNode queryAvailabilityNode,
            BookAppointmentNode bookAppointmentNode,
            CancelAppointmentNode cancelAppointmentNode,
            ResponseAssembleNode responseAssembleNode,
            IntentRouter intentRouter,
            MongoChatMemoryStore mongoChatMemoryStore,
            @Qualifier("xiaozhiAgent") XiaozhiAgent xiaozhiAgent
    ) {
        return new com.atguigu.java.ai.langchain4j.workflow.service.XiaozhiWorkflowService(
                intentClassifyNode, slotCollectNode, confirmValidateNode,
                queryAvailabilityNode, bookAppointmentNode, cancelAppointmentNode,
                responseAssembleNode, intentRouter, mongoChatMemoryStore, xiaozhiAgent
        );
    }
}
