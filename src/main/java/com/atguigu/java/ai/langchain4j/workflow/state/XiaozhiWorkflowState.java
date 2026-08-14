package com.atguigu.java.ai.langchain4j.workflow.state;

import dev.langchain4j.data.message.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 工作流 State Channels：节点之间流转的唯一状态对象。
 * 按生命周期分三类：输入通道 / 中间通道 / 输出通道。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XiaozhiWorkflowState {

    // ============== 输入通道（不变：初始化时写入） ==============
    /** 会话 ID（memoryId，对应 MongoChatMemoryStore 的 key） */
    private Long memoryId;
    /** 当前轮用户输入原文 */
    private String userMessage;
    /** 历史对话（从 MongoChatMemoryStore 读取，只读） */
    @Builder.Default
    private List<ChatMessage> history = new LinkedList<>();

    // ============== 中间通道（节点读写） ==============
    /** 意图分类结果（IntentClassifyNode 写入） */
    private Intent intent;
    /** Router 路由分支标识 */
    private Branch branch;
    /** 意图分类置信度（0.0~1.0，仅用于日志/观测） */
    private Double confidence;

    /** 挂号/取消场景槽位：key 来自 {@link SlotKeys} */
    @Builder.Default
    private Map<String, String> slotMap = new HashMap<>();
    /** 是否需要继续追问收集槽位 */
    @Builder.Default
    private boolean needCollectSlots = false;
    /** 下一轮要抛给用户的追问/确认话术（SlotCollectNode 或 Confirm 节点写入） */
    private String nextQuestion;

    /** 号源查询结果（QueryAvailabilityNode 写入） */
    private Boolean hasAvailability;
    /** 用户是否已确认预约/取消信息（用于入库前三重断言） */
    @Builder.Default
    private boolean hasUserConfirmation = false;

    /** Tool 执行轨迹（脱敏）：用于日志/审计，如 "预约挂号(name=张三,idCard=1101****1234)" */
    @Builder.Default
    private List<String> toolCallTraces = new ArrayList<>();
    /** 每节点耗时（ms）：key=节点类名，可观测性使用 */
    @Builder.Default
    private Map<String, Long> stepCostMs = new HashMap<>();

    /** 业务动作结果：预约成功/取消成功/号源 false 等 */
    private boolean hasBooked;
    private boolean hasCancelled;

    // ============== 输出通道（最终节点写入） ==============
    /** 待流式返回给前端的最终响应文本 */
    private String finalResponse;
    /** 是否命中 fallback 分支（仅日志记录用） */
    @Builder.Default
    private boolean fallbackTriggered = false;
    /** fallback / 节点异常的摘要信息（脱敏） */
    private String fallbackReason;
}
