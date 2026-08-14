package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.workflow.state.Branch;
import com.atguigu.java.ai.langchain4j.workflow.state.Intent;
import com.atguigu.java.ai.langchain4j.workflow.state.SlotKeys;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流节点 1：意图分类 + 槽位初步提取。
 * 构造函数注入 {@link ChatLanguageModel}，无状态，apply 每次调用独立。
 *
 * 让大模型返回 JSON 结构：
 * {
 *   "intent": "APPOINTMENT|CANCEL|TRIAGE|CHAT",
 *   "confidence": 0.95,
 *   "extractedSlots": {
 *       "name": "张三",
 *       "idCard": "...",
 *       "department": "...",
 *       "date": "YYYY-MM-DD",
 *       "time": "上午|下午",
 *       "doctorName": "李医生（可选）"
 *   }
 * }
 */
public class IntentClassifyNode {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifyNode.class);
    private static final int MAX_RETRIES = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern INTENT_REGEX = Pattern.compile("\"intent\"\\s*:\\s*\"([A-Z]+)\"");
    private static final Pattern CONFIDENCE_REGEX = Pattern.compile("\"confidence\"\\s*:\\s*([0-9.]+)");

    /** 构造函数注入（不使用 @Autowired 字段注入，便于 Mock 单测） */
    private final ChatLanguageModel chatLanguageModel;

    public IntentClassifyNode(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    /** 系统 Prompt：医疗场景意图分类说明 + JSON 输出强约束 */
    private static final String SYSTEM_PROMPT = String.join("\n",
            "你是一个严格的医疗意图分类器。你的任务只有一个：判断用户消息的意图，并从用户消息中提取已经提供的挂号槽位。",
            "意图值只允许 4 种（严格输出大写英文枚举）：",
            "  APPOINTMENT : 用户要预约挂号（关键词：挂号、预约、挂xx科、想看病、预约医生等）",
            "  CANCEL      : 用户要取消已有的挂号预约（关键词：取消、退号、撤销预约等）",
            "  TRIAGE      : 用户描述症状或健康问题希望得到科室推荐/医疗建议（关键词：头疼、难受、xx科怎么选、我是不是该挂xx科等）",
            "  CHAT        : 闲聊或其他（打招呼、感谢、问你是谁等非业务话题）",
            "",
            "提取槽位规则（只提取用户原文里明确提到的信息，禁止编造）：",
            "  - name        : 用户姓名（字符串）",
            "  - idCard      : 18 位身份证号（字符串）",
            "  - department  : 科室名称，如神经内科、心内科（字符串）",
            "  - date        : 日期，必须格式化为 YYYY-MM-DD。如果用户说"明天"、"后天"不要推理具体日期，留空字符串。",
            "  - time        : 时间段，只允许枚举值 上午 或 下午（字符串）",
            "  - doctorName  : 医生姓名，可选，没提就不传（字符串）",
            "",
            "输出格式严格 JSON（不要输出 Markdown 代码块、不要任何解释文字、不要 ```json 包裹）：",
            "{\"intent\":\"...\",\"confidence\":0.XX,\"extractedSlots\":{\"name\":\"...\",\"idCard\":\"...\",\"department\":\"...\",\"date\":\"...\",\"time\":\"...\",\"doctorName\":\"...\"}}"
    );

    /**
     * 执行节点：意图分类 + 槽位提取，结果写入 State
     *
     * @param state 当前工作流状态（仅读 memoryId/userMessage）
     * @return 返回同一个 state 对象（流式便于节点链式调用）
     */
    public XiaozhiWorkflowState apply(XiaozhiWorkflowState state) {
        long start = System.currentTimeMillis();
        String rawText = null;
        Exception lastErr = null;

        // 重试 3 次：LLM 可能返回非 JSON 文本
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<ChatMessage> messages = new ArrayList<>();
                messages.add(SystemMessage.from(SYSTEM_PROMPT));
                messages.add(UserMessage.from(state.getUserMessage()));

                Response<AiMessage> resp = chatLanguageModel.generate(messages);
                rawText = resp.content().text();

                // 去掉 ```json ... ``` 包裹（防御性）
                String jsonStr = unwrapJson(rawText);
                JsonNode root = MAPPER.readTree(jsonStr);

                String intentStr = root.path("intent").asText("").toUpperCase();
                Intent intent = Intent.valueOf(intentStr);

                double confidence = root.path("confidence").asDouble(0.0);
                if (confidence <= 0) {
                    Matcher cm = CONFIDENCE_REGEX.matcher(jsonStr);
                    if (cm.find()) confidence = Double.parseDouble(cm.group(1));
                }

                // 提取槽位
                Map<String, String> slots = new HashMap<>();
                JsonNode slotsNode = root.path("extractedSlots");
                if (slotsNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> it = slotsNode.fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> e = it.next();
                        String v = e.getValue().asText("");
                        if (v != null && !v.isBlank()) {
                            slots.put(e.getKey(), v.trim());
                        }
                    }
                }

                // 写入 state
                state.setIntent(intent);
                state.setConfidence(confidence);
                if (state.getSlotMap() == null) state.setSlotMap(new HashMap<>());
                state.getSlotMap().putAll(slots);

                // 根据 intent 映射 branch（分诊=AGENT_RAG，闲聊=AGENT_DIRECT，其余走工作流）
                state.setBranch(mapIntentToBranch(intent));

                long cost = System.currentTimeMillis() - start;
                state.getStepCostMs().put("IntentClassifyNode", cost);
                log.info("[Workflow][IntentClassifyNode] memoryId={} intent={} branch={} conf={} costMs={} slotsMask={}",
                        state.getMemoryId(), intent, state.getBranch(), confidence, cost, maskSlots(slots));
                return state;

            } catch (Exception e) {
                lastErr = e;
                log.warn("[Workflow][IntentClassifyNode] memoryId={} attempt={} parse fail, raw={}, err={}",
                        state.getMemoryId(), attempt, truncate(rawText, 120), e.getMessage());

                // 第 3 次失败后用正则回退（只抓 intent）
                if (attempt == MAX_RETRIES && rawText != null) {
                    try {
                        Matcher m = INTENT_REGEX.matcher(rawText);
                        if (m.find()) {
                            Intent intent = Intent.valueOf(m.group(1).toUpperCase());
                            state.setIntent(intent);
                            state.setBranch(mapIntentToBranch(intent));
                            state.setConfidence(0.5);
                            state.getStepCostMs().put("IntentClassifyNode", System.currentTimeMillis() - start);
                            log.warn("[Workflow][IntentClassifyNode] memoryId={} regex fallback success, intent={}", state.getMemoryId(), intent);
                            return state;
                        }
                    } catch (Exception ignored) { /* 正则也失败，最终 fallback */ }
                }
            }
        }

        // 3 次 + 正则都失败 → FALLBACK 分支
        state.setBranch(Branch.FALLBACK);
        state.setFallbackTriggered(true);
        state.setFallbackReason("IntentClassify parse failed after " + MAX_RETRIES + " retries: "
                + (lastErr == null ? "unknown" : lastErr.getMessage()));
        state.getStepCostMs().put("IntentClassifyNode", System.currentTimeMillis() - start);
        log.warn("[Workflow][IntentClassifyNode] memoryId={} FALLBACK triggered: {}", state.getMemoryId(), state.getFallbackReason());
        return state;
    }

    // ============== 内部工具方法 ==============

    private Branch mapIntentToBranch(Intent intent) {
        return switch (intent) {
            case APPOINTMENT -> Branch.WORKFLOW_APPOINTMENT;
            case CANCEL      -> Branch.WORKFLOW_CANCEL;
            case TRIAGE      -> Branch.AGENT_RAG;
            case CHAT        -> Branch.AGENT_DIRECT;
        };
    }

    /** 去掉 ```json ``` 或 ``` ``` 包裹（防御性） */
    private String unwrapJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int end = s.lastIndexOf("```");
            if (end > 3) {
                s = s.substring(3, end);
                if (s.startsWith("json")) s = s.substring(4);
                s = s.trim();
            }
        }
        // 找到第一个 { 和最后一个 }
        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return s.substring(first, last + 1);
        }
        return s;
    }

    /** 日志用：槽位值脱敏后输出（身份证明文不出现在日志） */
    private Map<String, String> maskSlots(Map<String, String> s) {
        Map<String, String> out = new HashMap<>();
        s.forEach((k, v) -> {
            if (SlotKeys.SLOT_IDCARD.equals(k)) {
                out.put(k, v == null || v.length() < 10 ? "***" : v.substring(0, 6) + "****" + v.substring(v.length() - 4));
            } else {
                out.put(k, v);
            }
        });
        return out;
    }

    private String truncate(String s, int len) {
        if (s == null) return null;
        return s.length() <= len ? s : s.substring(0, len) + "...";
    }
}
