package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.workflow.state.Intent;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流节点 2：槽位缺失/格式校验节点（纯函数，无任何外部依赖，单测秒跑）。
 * 职责：
 *   1. 联合 IntentClassifyNode 提取的 slotMap + 历史 UserMessage 正则回填，尽可能补全槽位
 *   2. 对挂号/取消场景的必填 5 项逐一跑 SlotValidator 格式校验
 *   3. 有错误 → State.needCollectSlots=true + nextQuestion 追问话术；无错误 → 放行
 */
public class SlotCollectNode {

    private static final Logger log = LoggerFactory.getLogger(SlotCollectNode.class);

    /** 纯函数：无构造参数、无注入、不访问 DB/LLM，apply 可重复调用 */
    public XiaozhiWorkflowState apply(XiaozhiWorkflowState state) {
        long start = System.currentTimeMillis();

        // 只有 APPOINTMENT / CANCEL 两种业务意图才跑槽位校验
        if (state.getIntent() != Intent.APPOINTMENT && state.getIntent() != Intent.CANCEL) {
            state.setNeedCollectSlots(false);
            state.getStepCostMs().put("SlotCollectNode", System.currentTimeMillis() - start);
            return state;
        }

        if (state.getSlotMap() == null) state.setSlotMap(new HashMap<>());

        // 历史 UserMessage 简单回溯：用户可能在前几轮已经说了身份证/日期，槽位没提取到的话用正则兜底
        String userHistory = state.getHistory().stream()
                .filter(m -> m instanceof UserMessage)
                .map(ChatMessage::text)
                .collect(Collectors.joining(" \n "));
        // 当前轮 + 历史 拼一起做正则回填（不覆盖已有值）
        String combined = state.getUserMessage() + " \n " + userHistory;
        SlotValidator.fillFromHistoryRegex(combined, state.getSlotMap());

        // 必填 5 项格式校验（Appointment / Cancel 共用 5 项）
        Map<String, String> errors = SlotValidator.checkAppointmentSlots(state.getSlotMap());
        boolean slotsOk = errors.isEmpty();
        state.setNeedCollectSlots(!slotsOk);

        if (!slotsOk) {
            state.setNextQuestion(SlotValidator.buildQuestion(errors));
            // 槽位缺的场景：直接把追问话术作为 finalResponse 返回，不跑后续节点
            state.setFinalResponse(state.getNextQuestion());
        }

        long cost = System.currentTimeMillis() - start;
        state.getStepCostMs().put("SlotCollectNode", cost);
        log.info("[Workflow][SlotCollectNode] memoryId={} intent={} slotsOk={} errCount={} costMs={}",
                state.getMemoryId(), state.getIntent(), slotsOk, errors.size(), cost);
        return state;
    }
}
