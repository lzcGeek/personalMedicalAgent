package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.workflow.state.Intent;
import com.atguigu.java.ai.langchain4j.workflow.state.SlotKeys;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流节点 N：最终响应拼接节点（纯函数）。
 * 汇总 State 中的所有中间结果（hasBooked / hasCancelled / hasAvailability / toolCallTraces 等），
 * 生成最终给用户的友好回复，并强制追加「医疗免责声明」（Prompt 模板要求的合规内容）。
 */
public class ResponseAssembleNode {

    /** 强追加：医疗合规免责声明（放在每段最终回复末尾，不依赖大模型是否记得加） */
    private static final String DISCLAIMER = "\n\n⚠️ 温馨提示：以上建议仅供就医参考，不能替代医师面诊诊断。如涉及急诊，请立即拨打 120 或前往医院急诊就诊。";

    public XiaozhiWorkflowState apply(XiaozhiWorkflowState state) {
        // 槽位缺项：nextQuestion 已由 SlotCollectNode 生成 → 直接复用，不必重写
        if (state.isNeedCollectSlots() && state.getFinalResponse() == null) {
            state.setFinalResponse(state.getNextQuestion() + DISCLAIMER);
            return state;
        }
        // Confirm 未过：直接复用 ConfirmValidateNode 的确认话术
        if (!state.isHasUserConfirmation() && state.getIntent() != null
                && (state.getIntent() == Intent.APPOINTMENT || state.getIntent() == Intent.CANCEL)
                && state.getFinalResponse() == null) {
            state.setFinalResponse(state.getNextQuestion() + DISCLAIMER);
            return state;
        }
        // 号源 false：组装"暂无号源"话术
        if (Boolean.FALSE.equals(state.getHasAvailability()) && state.getFinalResponse() == null) {
            Map<String, String> m = state.getSlotMap() == null ? new LinkedHashMap<>() : state.getSlotMap();
            state.setFinalResponse(String.format(
                    "抱歉哦 😥，%s 在 %s（%s）暂时没有可预约的号源。您可以换个日期或科室，我可以再帮您查询。",
                    v(m.get(SlotKeys.SLOT_DEPARTMENT)),
                    v(m.get(SlotKeys.SLOT_DATE)),
                    v(m.get(SlotKeys.SLOT_TIME))
            ) + DISCLAIMER);
            return state;
        }
        // 业务节点已经写入了 finalResponse（bookAppointment 返回的"预约成功"等），这里只加免责声明 + 友好 emoji 包装
        if (state.getFinalResponse() != null) {
            String body = state.getFinalResponse();
            if (state.isHasBooked()) {
                body = "🎉 " + body + appendSlotSummary(state, true);
            } else if (state.isHasCancelled()) {
                body = "✅ " + body + appendSlotSummary(state, false);
            }
            // 如果没加过免责声明就加上
            if (!body.contains("仅供就医参考")) {
                body = body + DISCLAIMER;
            }
            state.setFinalResponse(body);
            return state;
        }
        // Fallback：兜底空响应
        state.setFinalResponse("（工作流未生成可返回内容，已切回 fallback）" + DISCLAIMER);
        return state;
    }

    private String appendSlotSummary(XiaozhiWorkflowState s, boolean booked) {
        Map<String, String> m = s.getSlotMap() == null ? new LinkedHashMap<>() : s.getSlotMap();
        String doc = m.get(SlotKeys.SLOT_DOCTOR);
        String doctor = (doc == null || doc.isBlank()) ? "（不指定）" : doc;
        String summary = String.format("【预约信息】姓名=%s，科室=%s，日期=%s，时段=%s，医生=%s。",
                v(m.get(SlotKeys.SLOT_NAME)),
                v(m.get(SlotKeys.SLOT_DEPARTMENT)),
                v(m.get(SlotKeys.SLOT_DATE)),
                v(m.get(SlotKeys.SLOT_TIME)),
                doctor);
        return "\n\n📋 " + summary + (booked ? "\n请您就诊当日提前 15 分钟到院取号哦～" : "");
    }

    private String v(String s) { return s == null || s.isBlank() ? "(未提供)" : s; }
}
