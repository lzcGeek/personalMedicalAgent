package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.workflow.state.Intent;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作流节点 5b：取消预约入库节点。
 * 两重前置断言（保证取消链路不可被跳过）：
 *   ① 槽位齐全合法（needCollectSlots == false）
 *   ② 用户已确认（hasUserConfirmation == true）—— 取消操作是破坏性的，必须用户确认
 *   注意：取消链路不需要号源查询（QueryAvailabilityNode），因为 AppointmentService.cancel 内部自身查"是否存在"
 */
public class CancelAppointmentNode {

    private static final Logger log = LoggerFactory.getLogger(CancelAppointmentNode.class);

    private final AppointmentService appointmentService;

    public CancelAppointmentNode(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    public XiaozhiWorkflowState apply(XiaozhiWorkflowState state) {
        long start = System.currentTimeMillis();

        if (state.isNeedCollectSlots()) {
            throw new IllegalStateException("CancelAppointmentNode 前置断言失败: 槽位未收集齐全");
        }
        if (state.getIntent() != Intent.CANCEL) {
            throw new IllegalStateException("CancelAppointmentNode 只能用于 CANCEL 意图，当前=" + state.getIntent());
        }
        if (!state.isHasUserConfirmation()) {
            throw new IllegalStateException("CancelAppointmentNode 前置断言失败: 用户尚未确认取消操作");
        }

        Appointment appointment = AppointmentMapper.fromSlotMap(state.getSlotMap());
        String result = appointmentService.cancelAppointment(appointment);
        state.setHasCancelled(result.contains("取消预约成功"));

        state.getToolCallTraces().add("取消预约挂号(" + AppointmentMapper.traceSummary(state.getSlotMap()) + ") → " + result);

        long cost = System.currentTimeMillis() - start;
        state.getStepCostMs().put("CancelAppointmentNode", cost);
        log.info("[Workflow][CancelAppointmentNode] memoryId={} result={} hasCancelled={} costMs={}",
                state.getMemoryId(), result, state.isHasCancelled(), cost);
        state.setFinalResponse(result);
        return state;
    }
}
