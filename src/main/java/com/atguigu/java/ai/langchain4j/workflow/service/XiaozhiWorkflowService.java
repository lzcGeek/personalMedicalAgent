package com.atguigu.java.ai.langchain4j.workflow.service;

import com.atguigu.java.ai.langchain4j.assistant.XiaozhiAgent;
import com.atguigu.java.ai.langchain4j.store.MongoChatMemoryStore;
import com.atguigu.java.ai.langchain4j.workflow.nodes.BookAppointmentNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.CancelAppointmentNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.ConfirmValidateNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.IntentClassifyNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.QueryAvailabilityNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.ResponseAssembleNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.SlotCollectNode;
import com.atguigu.java.ai.langchain4j.workflow.router.IntentRouter;
import com.atguigu.java.ai.langchain4j.workflow.state.Branch;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流总编排入口：把 Router + 所有 Node 串起来，并处理流式返回、记忆白名单写入、异常 Fallback。
 * 注意：这是编排层，不要写业务逻辑；判断、校验、组装全部下沉到对应 Node 纯函数。
 *
 * 设计决策：
 *   - 构造函数注入所有 Node（不使用 @Autowired 字段，确保可测试）
 *   - 对外方法只有一个：streamChat(Long, String) 返回 Flux<String>
 *   - 分诊/闲聊场景：直接把调用委托给 XiaozhiAgent.chat，走原 Agent 完整链路（含 RAG 检索）
 *   - 异常兜底：工作流任一步抛异常 → 切到 XiaozhiAgent.chat Fallback
 */
public class XiaozhiWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(XiaozhiWorkflowService.class);

    private final IntentClassifyNode intentClassifyNode;
    private final SlotCollectNode slotCollectNode;
    private final ConfirmValidateNode confirmValidateNode;
    private final QueryAvailabilityNode queryAvailabilityNode;
    private final BookAppointmentNode bookAppointmentNode;
    private final CancelAppointmentNode cancelAppointmentNode;
    private final ResponseAssembleNode responseAssembleNode;
    private final IntentRouter intentRouter;
    private final MongoChatMemoryStore mongoChatMemoryStore;
    private final XiaozhiAgent xiaozhiAgent; // Fallback + 分诊/闲聊路径

    public XiaozhiWorkflowService(
            IntentClassifyNode intentClassifyNode,
            SlotCollectNode slotCollectNode,
            ConfirmValidateNode confirmValidateNode,
            QueryAvailabilityNode queryAvailabilityNode,
            BookAppointmentNode bookAppointmentNode,
            CancelAppointmentNode cancelAppointmentNode,
            ResponseAssembleNode responseAssembleNode,
            IntentRouter intentRouter,
            MongoChatMemoryStore mongoChatMemoryStore,
            XiaozhiAgent xiaozhiAgent) {
        this.intentClassifyNode = intentClassifyNode;
        this.slotCollectNode = slotCollectNode;
        this.confirmValidateNode = confirmValidateNode;
        this.queryAvailabilityNode = queryAvailabilityNode;
        this.bookAppointmentNode = bookAppointmentNode;
        this.cancelAppointmentNode = cancelAppointmentNode;
        this.responseAssembleNode = responseAssembleNode;
        this.intentRouter = intentRouter;
        this.mongoChatMemoryStore = mongoChatMemoryStore;
        this.xiaozhiAgent = xiaozhiAgent;
    }

    /**
     * 对外主入口：流式对话。
     *
     * @param memoryId   会话 ID（对应 MongoChatMemoryStore key）
     * @param userMessage 用户这轮输入
     * @return Flux<String> 流式字符串（直接透传给 XiaozhiController produces=text/stream）
     */
    public Flux<String> streamChat(Long memoryId, String userMessage) {
        long workflowStart = System.currentTimeMillis();
        try {
            // 1. 初始化 State Channels
            XiaozhiWorkflowState state = XiaozhiWorkflowState.builder()
                    .memoryId(memoryId)
                    .userMessage(userMessage)
                    .history(new ArrayList<>(mongoChatMemoryStore.getMessages(memoryId)))
                    .build();

            // 2. 节点 1：意图分类
            state = intentClassifyNode.apply(state);

            // 3. 路由分支
            String path = intentRouter.route(state);
            log.info("[Workflow] memoryId={} path={} intent={} branch={} confidence={}",
                    memoryId, path, state.getIntent(), state.getBranch(), state.getConfidence());

            // ===== 分支 A：分诊/闲聊 → 直接委托原 Agent（含 RAG + Streaming 流式能力）=====
            if (IntentRouter.AGENT_PATH.equals(path) || state.getBranch() == Branch.AGENT_RAG || state.getBranch() == Branch.AGENT_DIRECT) {
                log.info("[Workflow] memoryId={} 走原 Agent 链路（分诊/闲聊）", memoryId);
                return xiaozhiAgent.chat(memoryId, userMessage);
            }

            // ===== 分支 B：Fallback → 委托原 Agent =====
            if (IntentRouter.AGENT_FALLBACK.equals(path) || state.getBranch() == Branch.FALLBACK) {
                warnFallback(memoryId, state.getFallbackReason() == null ? "intent classify fallback branch" : state.getFallbackReason());
                return xiaozhiAgent.chat(memoryId, userMessage);
            }

            // ===== 分支 C：工作流链路（APPOINTMENT / CANCEL）=====
            // 4. SlotCollectNode：必填 5 项齐全 + 格式合法校验
            state = slotCollectNode.apply(state);
            // 槽位缺：直接返回追问话术（不走后续业务节点），但仍需 Router 确认
            String afterSlotPath = intentRouter.route(state);
            if (IntentRouter.SLOT_QUESTION_RETURN.equals(afterSlotPath)) {
                state = responseAssembleNode.apply(state);
                return persistAndReturnStream(state, memoryId, userMessage, workflowStart);
            }

            // ===== APPOINTMENT / CANCEL 具体链路分发 =====
            String finalResponse;
            if (Branch.WORKFLOW_APPOINTMENT == state.getBranch()) {
                // 挂号链路：QueryAvailability → ConfirmValidate → Book → Assemble
                //   如果号源之前未知，先跑 Query
                if (state.getHasAvailability() == null) {
                    state = queryAvailabilityNode.apply(state);
                }
                String afterQueryPath = intentRouter.route(state);
                if (IntentRouter.DIRECT_ASSEMBLE.equals(afterQueryPath)) {
                    // 号源 false：直接组装"暂无号源"回复
                    state = responseAssembleNode.apply(state);
                    return persistAndReturnStream(state, memoryId, userMessage, workflowStart);
                }
                // Confirm 节点：用户是否"确认"
                state = confirmValidateNode.apply(state);
                if (!state.isHasUserConfirmation()) {
                    state = responseAssembleNode.apply(state);
                    return persistAndReturnStream(state, memoryId, userMessage, workflowStart);
                }
                // 三重断言均过 → 入库（BookAppointmentNode 内部再次抛异常防御）
                state = bookAppointmentNode.apply(state);
                state = responseAssembleNode.apply(state);
                return persistAndReturnStream(state, memoryId, userMessage, workflowStart);

            } else {
                // CANCEL 链路：ConfirmValidate → Cancel → Assemble  (QueryAvailability 非必须，CancelNode 内部查存在性)
                state = confirmValidateNode.apply(state);
                if (!state.isHasUserConfirmation()) {
                    state = responseAssembleNode.apply(state);
                    return persistAndReturnStream(state, memoryId, userMessage, workflowStart);
                }
                state = cancelAppointmentNode.apply(state);
                state = responseAssembleNode.apply(state);
                return persistAndReturnStream(state, memoryId, userMessage, workflowStart);
            }

        } catch (Throwable t) {
            // ===== 全局异常 → Fallback 到原 Agent（用户绝对不感知 500）=====
            warnFallback(memoryId, "workflow exception: " + t.getClass().getSimpleName() + " -> " + t.getMessage());
            try {
                return xiaozhiAgent.chat(memoryId, userMessage);
            } catch (Throwable t2) {
                // Agent 本身也挂的情况下才退化为非流式字符串（极端情况）
                log.error("[Workflow] memoryId={} Agent fallback also failed: {}", memoryId, t2.getMessage());
                return Flux.just("系统繁忙，请稍后重试～（如需挂号/取消预约，您也可以拨打医院预约电话）");
            }
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 工作流链路最后一步：①把「UserMessage + AiMessage」白名单写入 MongoDB；
     * ②把 finalResponse 切成 Flux 流式返回（模拟流式 token，前端体验一致）。
     *
     * 这里没有用 qwenStreamingChatModel 直接流式，因为挂号/取消链路的最终回复是
     * "预约成功"、"信息需要确认"等明确话术，用本地按字符切 Flux 足够稳定，无
     * 额外 LLM 调用，首字延迟 <50ms。后续如需在回复里接入"根据 RAG 推荐科室医生"
     * 等生成式内容，可独立替换为流式 LLM 调用即可（此处接口透明）。
     */
    private Flux<String> persistAndReturnStream(
            XiaozhiWorkflowState state,
            Long memoryId,
            String userMessage,
            long workflowStart
    ) {
        // 1. 白名单写入记忆：只把这一轮的「用户输入 + 最终 AI 回复」追加到 ChatMemory
        //    读取旧 messages → 追加一对 → updateMessages
        try {
            List<ChatMessage> old = mongoChatMemoryStore.getMessages(memoryId);
            List<ChatMessage> updated = new ArrayList<>(old);
            updated.add(UserMessage.from(userMessage));
            String body = state.getFinalResponse() == null ? "" : state.getFinalResponse();
            updated.add(AiMessage.from(body));
            mongoChatMemoryStore.updateMessages(memoryId, updated);
            log.info("[Workflow] memoryId={} 白名单写入记忆完成: messageCount={}, costTotalMs={}",
                    memoryId, updated.size(), System.currentTimeMillis() - workflowStart);
        } catch (Throwable writeErr) {
            log.error("[Workflow] memoryId={} 记忆写入失败（不中断用户响应）: {}", memoryId, writeErr.getMessage());
        }

        // 2. 按字符切流式返回（每 40ms 一个字符，约 25 字/秒，接近真实中文语速）
        String text = state.getFinalResponse() == null ? "" : state.getFinalResponse();
        if (text.isEmpty()) {
            return Flux.just("");
        }
        List<String> chars = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) chars.add(String.valueOf(text.charAt(i)));
        return Flux.fromIterable(chars)
                .delayElements(Duration.ofMillis(40));
    }

    private void warnFallback(Long memoryId, String reason) {
        log.warn("[Workflow] memoryId={} FALLBACK → XiaozhiAgent, reason={}", memoryId, reason);
    }
}
