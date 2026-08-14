package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.workflow.state.SlotKeys;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流节点 3：确认校验节点（纯函数）。
 * 在 QueryAvailabilityNode 放行后、BookAppointmentNode 入库前，强制问一次"信息是否确认"。
 *
 * hasUserConfirmation 的判定逻辑：当前用户消息是对"是否确认"上一轮追问的正面响应。
 * 这一版用启发式关键词判断（是/确认/对/好/没错 等），确认句里带否定关键词（不/错/否/不是）视为未确认。
 * 更强方案是引入一个小的 LLM yes/no 分类，这里为了性能先用纯函数。
 */
public class ConfirmValidateNode {

    private static final Logger log = LoggerFactory.getLogger(ConfirmValidateNode.class);

    /** 确认关键词（任意命中一个即可算"用户已确认"） */
    private static final String[] YES_WORDS = {"确认", "是的", "是", "对", "对的", "没错", "好的", "好", "ok", "OK", "没问题"};
    /** 否定词（出现即推翻"已确认"） */
    private static final String[] NO_WORDS = {"不确认", "不对", "不是", "否", "错了", "错误", "改一下", "换一个", "取消"};

    public XiaozhiWorkflowState apply(XiaozhiWorkflowState state) {
        long start = System.currentTimeMillis();

        // 非 APPOINTMENT / CANCEL 场景：永远放行
        boolean appointmentFlow = state.getSlotMap() != null && !state.getSlotMap().isEmpty();
        if (!appointmentFlow) {
            state.setHasUserConfirmation(true);
            state.getStepCostMs().put("ConfirmValidateNode", System.currentTimeMillis() - start);
            return state;
        }

        String msg = state.getUserMessage() == null ? "" : state.getUserMessage().trim();

        // 1. 先找否定词
        for (String no : NO_WORDS) {
            if (msg.contains(no)) {
                state.setHasUserConfirmation(false);
                state.setNextQuestion("好的，请您告诉我需要修改的信息。" + buildSummary(state));
                logConfirm(state, start, false, "否定词命中:" + no);
                return state;
            }
        }

        // 2. 再找肯定词
        boolean yes = false;
        for (String y : YES_WORDS) {
            if (msg.contains(y)) {
                yes = true;
                break;
            }
        }

        // 3. 兜底：当这一轮用户消息就是对确认询问的直接回复（短文本 ≤6 且没信息增量时，默认放行）
        if (!yes && msg.length() <= 6 && !containsNewInfo(msg, state)) {
            // 很短的无信息回复（如"嗯"/"啊"），保守算"未确认"，再次问确认话术
            state.setHasUserConfirmation(false);
            state.setNextQuestion("请确认以下预约信息是否正确：" + buildSummary(state) + "（请回复「确认」或说明需要修改的内容）");
            logConfirm(state, start, false, "短文本无有效确认");
            return state;
        }

        state.setHasUserConfirmation(yes);
        if (!yes) {
            // 用户这一轮补充了新的槽位信息（如 "改成心内科"），先不确认，让 SlotCollectNode 下一轮再校验
            state.setNextQuestion("信息有更新，请重新核对：" + buildSummary(state) + "，确认无误请回复「确认」。");
        }
        logConfirm(state, start, yes, yes ? "关键词命中" : "本轮为槽位增量，未确认");
        return state;
    }

    private void logConfirm(XiaozhiWorkflowState state, long start, boolean ok, String reason) {
        state.getStepCostMs().put("ConfirmValidateNode", System.currentTimeMillis() - start);
        log.info("[Workflow][ConfirmValidateNode] memoryId={} confirmed={} reason={} costMs={}",
                state.getMemoryId(), ok, reason, System.currentTimeMillis() - start);
    }

    /** 生成预约信息确认摘要（脱敏身份证） */
    private String buildSummary(XiaozhiWorkflowState s) {
        Map<String, String> sm = s.getSlotMap() == null ? new LinkedHashMap<>() : s.getSlotMap();
        StringBuilder sb = new StringBuilder();
        sb.append("姓名=").append(v(sm.get(SlotKeys.SLOT_NAME))).append("，");
        String id = sm.get(SlotKeys.SLOT_IDCARD);
        sb.append("身份证=").append(id == null ? "未提供" : (id.length() >= 14 ? id.substring(0, 6) + "****" + id.substring(id.length() - 4) : id)).append("，");
        sb.append("科室=").append(v(sm.get(SlotKeys.SLOT_DEPARTMENT))).append("，");
        sb.append("日期=").append(v(sm.get(SlotKeys.SLOT_DATE))).append("，");
        sb.append("时段=").append(v(sm.get(SlotKeys.SLOT_TIME))).append("，");
        String doc = sm.get(SlotKeys.SLOT_DOCTOR);
        if (doc != null && !doc.isBlank()) sb.append("医生=").append(doc).append("。");
        else sb.append("医生=（不指定）。");
        return sb.toString();
    }

    /** 判断当前用户消息是否包含新的槽位信息（简单启发式：包含新数字 10 位以上、科室关键词、日期格式子串） */
    private boolean containsNewInfo(String msg, XiaozhiWorkflowState state) {
        if (msg == null) return false;
        // 包含 YYYY-MM-DD
        if (msg.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) return true;
        // 包含 10+ 位连续数字（身份证或手机）
        if (msg.matches(".*\\d{10,}.*")) return true;
        // 包含"科"字（新科室）或 上午/下午
        return msg.contains("科") || msg.contains("上午") || msg.contains("下午");
    }

    private String v(String s) {
        return s == null || s.isBlank() ? "（未提供）" : s;
    }
}
