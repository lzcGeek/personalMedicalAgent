package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.workflow.state.Intent;
import com.atguigu.java.ai.langchain4j.workflow.state.SlotKeys;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作流节点 4：号源查询。
 * 前置断言（AC-3 强约束保证）：① Intent == APPOINTMENT / CANCEL ② 槽位齐全 ③ 格式合法
 *   不满足 → 抛 IllegalStateException，Router 前面应该拦截，这里只为防御式编程
 * 写入 State.hasAvailability = 查询结果
 */
public class QueryAvailabilityNode {

    private static final Logger log = LoggerFactory.getLogger(QueryAvailabilityNode.class);

    private final AppointmentService appointmentService;

    /** 构造函数注入（不使用 @Autowired 字段注入，Mock 友好） */
    public QueryAvailabilityNode(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    public XiaozhiWorkflowState apply(XiaozhiWorkflowState state) {
        long start = System.currentTimeMillis();

        // 防御式前置断言：槽位不齐不允许进查询节点（Router 应先把它导回 SlotCollectNode）
        if (state.isNeedCollectSlots()) {
            throw new IllegalStateException("QueryAvailabilityNode 前置断言失败: 槽位未收集齐全，需先执行 SlotCollectNode");
        }
        if (state.getIntent() != Intent.APPOINTMENT && state.getIntent() != Intent.CANCEL) {
            // CANCEL 链路也走这个节点，仅为查询"是否有这个记录"（不过 AppointmentService.cancel 本身查了，这里双保险不跑也行）
            if (state.getIntent() == Intent.CANCEL) {
                // 取消链路跳过号源查询，直接走"是否有记录"判定（由 CancelNode 自己查）
                state.getStepCostMs().put("QueryAvailabilityNode", System.currentTimeMillis() - start);
                return state;
            }
            throw new IllegalStateException("QueryAvailabilityNode 只能处理 APPOINTMENT/CANCEL 意图，当前=" + state.getIntent());
        }

        String dept = state.getSlotMap().get(SlotKeys.SLOT_DEPARTMENT);
        String date = state.getSlotMap().get(SlotKeys.SLOT_DATE);
        String time = state.getSlotMap().get(SlotKeys.SLOT_TIME);
        String doctor = state.getSlotMap().get(SlotKeys.SLOT_DOCTOR);

        boolean ok = appointmentService.queryAvailability(dept, date, time, doctor);
        state.setHasAvailability(ok);
        state.getToolCallTraces().add("查询是否有号源(" +
                "dept=" + dept + ",date=" + date + ",time=" + time + ",doctor=" + (doctor == null ? "无" : doctor) +
                ") → " + (ok ? "有号源" : "无号源"));

        long cost = System.currentTimeMillis() - start;
        state.getStepCostMs().put("QueryAvailabilityNode", cost);
        log.info("[Workflow][QueryAvailabilityNode] memoryId={} dept={} date={} time={} doctor={} result={} costMs={}",
                state.getMemoryId(), dept, date, time, doctor, ok, cost);
        return state;
    }
}
