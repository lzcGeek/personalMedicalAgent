package com.atguigu.java.ai.langchain4j.workflow;

import com.atguigu.java.ai.langchain4j.workflow.router.IntentRouter;
import com.atguigu.java.ai.langchain4j.workflow.state.Branch;
import com.atguigu.java.ai.langchain4j.workflow.state.Intent;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 8 测试文件 2：IntentRouter（纯函数路由）+ SlotCollectNode + ConfirmValidateNode 组合单测。
 * 覆盖 AC-2：4 种意图 × 3 种槽位状态组合路由结果。
 */
class IntentRouterTest {

    private IntentRouter router;
    private SlotCollectNode slotCollectNode;
    private ConfirmValidateNode confirmValidateNode;

    @BeforeEach
    void setup() {
        router = new IntentRouter();
        slotCollectNode = new SlotCollectNode();
        confirmValidateNode = new ConfirmValidateNode();
    }

    // ====== 4 种意图 首次 route 结果 ======

    @Test @DisplayName("APPOINTMENT → WORKFLOW_APPOINTMENT 路由")
    void routeAppointment() {
        XiaozhiWorkflowState s = base(Intent.APPOINTMENT, Branch.WORKFLOW_APPOINTMENT);
        // 还没跑 SlotCollectNode → 槽位空 → 跑完 SlotCollect 后 route 应该 SLOT_QUESTION_RETURN
        s = slotCollectNode.apply(s);
        assertTrue(s.isNeedCollectSlots());
        assertEquals(IntentRouter.SLOT_QUESTION_RETURN, router.route(s));
    }

    @Test @DisplayName("CANCEL → WORKFLOW_CANCEL，槽位缺 → SLOT_QUESTION_RETURN")
    void routeCancel() {
        XiaozhiWorkflowState s = base(Intent.CANCEL, Branch.WORKFLOW_CANCEL);
        s = slotCollectNode.apply(s);
        assertTrue(s.isNeedCollectSlots());
        assertEquals(IntentRouter.SLOT_QUESTION_RETURN, router.route(s));
    }

    @Test @DisplayName("TRIAGE → AGENT_RAG → AGENT_PATH")
    void routeTriage() {
        XiaozhiWorkflowState s = base(Intent.TRIAGE, Branch.AGENT_RAG);
        assertEquals(IntentRouter.AGENT_PATH, router.route(s));
    }

    @Test @DisplayName("CHAT → AGENT_DIRECT → AGENT_PATH")
    void routeChat() {
        XiaozhiWorkflowState s = base(Intent.CHAT, Branch.AGENT_DIRECT);
        assertEquals(IntentRouter.AGENT_PATH, router.route(s));
    }

    @Test @DisplayName("FALLBACK → AGENT_FALLBACK")
    void routeFallback() {
        XiaozhiWorkflowState s = base(Intent.CHAT, Branch.FALLBACK);
        assertEquals(IntentRouter.AGENT_FALLBACK, router.route(s));
    }

    // ====== APPOINTMENT 不同阶段路由 ======

    @Test @DisplayName("APPOINTMENT 槽位齐全 + 号源未知 → QUERY_CONFIRM_BOOK")
    void apptOkQueryUnknown() {
        XiaozhiWorkflowState s = base(Intent.APPOINTMENT, Branch.WORKFLOW_APPOINTMENT);
        s.setSlotMap(fullOkSlots());
        s = slotCollectNode.apply(s);
        assertFalse(s.isNeedCollectSlots());
        assertEquals(IntentRouter.QUERY_CONFIRM_BOOK, router.route(s));
    }

    @Test @DisplayName("APPOINTMENT 号源 false → DIRECT_ASSEMBLE（直接回复暂无号源）")
    void apptNoAvailability() {
        XiaozhiWorkflowState s = base(Intent.APPOINTMENT, Branch.WORKFLOW_APPOINTMENT);
        s.setSlotMap(fullOkSlots());
        s.setHasAvailability(false);
        assertEquals(IntentRouter.DIRECT_ASSEMBLE, router.route(s));
    }

    @Test @DisplayName("APPOINTMENT 号源 true → QUERY_CONFIRM_BOOK")
    void apptHasAvailability() {
        XiaozhiWorkflowState s = base(Intent.APPOINTMENT, Branch.WORKFLOW_APPOINTMENT);
        s.setSlotMap(fullOkSlots());
        s.setHasAvailability(true);
        assertEquals(IntentRouter.QUERY_CONFIRM_BOOK, router.route(s));
    }

    // ====== Confirm 节点行为 ======

    @Test @DisplayName("确认节点：用户回复"确认" → hasUserConfirmation=true")
    void confirmYes() {
        XiaozhiWorkflowState s = base(Intent.APPOINTMENT, Branch.WORKFLOW_APPOINTMENT);
        s.setSlotMap(fullOkSlots());
        s.setUserMessage("确认");
        s = confirmValidateNode.apply(s);
        assertTrue(s.isHasUserConfirmation());
    }

    @Test @DisplayName("确认节点：用户回复"不对" → 否定词命中，确认=false + 追问确认信息")
    void confirmNo() {
        XiaozhiWorkflowState s = base(Intent.APPOINTMENT, Branch.WORKFLOW_APPOINTMENT);
        s.setSlotMap(fullOkSlots());
        s.setUserMessage("不对，我要改科室");
        s = confirmValidateNode.apply(s);
        assertFalse(s.isHasUserConfirmation());
        assertNotNull(s.getNextQuestion());
        assertTrue(s.getNextQuestion().contains("修改"), s.getNextQuestion());
    }

    @Test @DisplayName("确认节点：用户只说"嗯"（短文本无确认词）→ hasConfirmation=false，再次提示")
    void confirmHmm() {
        XiaozhiWorkflowState s = base(Intent.APPOINTMENT, Branch.WORKFLOW_APPOINTMENT);
        s.setSlotMap(fullOkSlots());
        s.setUserMessage("嗯");
        s = confirmValidateNode.apply(s);
        assertFalse(s.isHasUserConfirmation());
        assertNotNull(s.getNextQuestion());
        assertTrue(s.getNextQuestion().contains("确认"), s.getNextQuestion());
    }

    // ====== helpers ======

    private XiaozhiWorkflowState base(Intent i, Branch b) {
        return XiaozhiWorkflowState.builder()
                .memoryId(999L)
                .userMessage("测试消息")
                .intent(i)
                .branch(b)
                .build();
    }

    private Map<String, String> fullOkSlots() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", "王五");
        m.put("idCard", "310101199501024321");
        m.put("department", "心内科");
        m.put("date", "2025-05-01");
        m.put("time", "上午");
        m.put("doctorName", "李医生");
        return m;
    }
}
