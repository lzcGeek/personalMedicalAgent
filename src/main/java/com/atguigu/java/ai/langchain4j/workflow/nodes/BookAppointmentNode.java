package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.workflow.state.Intent;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作流节点 5a：预约入库节点。
 * 三重前置断言（硬约束，任何一项失败抛 IllegalStateException，保证挂号流程不可被跳过）：
 *   ① SlotCollectNode 判定槽位齐全合法（needCollectSlots == false）
 *   ② QueryAvailabilityNode 判定有号源（hasAvailability == true）
 *   ③ ConfirmValidateNode 判定用户已确认（hasUserConfirmation == true）
 */
public class BookAppointmentNode {

    private static final Logger log = LoggerFactory.getLogger(BookAppointmentNode.class);

    private final AppointmentService appointmentService;

    public BookAppointmentNode(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    public XiaozhiWorkflowState apply(XiaozhiWorkflowState state) {
        long start = System.currentTimeMillis();

        // 三重重言（AC-3 强约束核心）
        if (state.isNeedCollectSlots()) {
            throw new IllegalStateException("BookAppointmentNode 前置断言失败: 槽位未收集齐全");
        }
        if (state.getIntent() != Intent.APPOINTMENT) {
            throw new IllegalStateException("BookAppointmentNode 只能用于 APPOINTMENT 意图，当前=" + state.getIntent());
        }
        if (state.getHasAvailability() == null || !state.getHasAvailability()) {
            throw new IllegalStateException("BookAppointmentNode 前置断言失败: 号源未查询或已无号");
        }
        if (!state.isHasUserConfirmation()) {
            throw new IllegalStateException("BookAppointmentNode 前置断言失败: 用户尚未确认预约信息");
        }

        Appointment appointment = AppointmentMapper.fromSlotMap(state.getSlotMap());
        String result = appointmentService.bookAppointment(appointment);
        state.setHasBooked(result.contains("预约成功"));

        state.getToolCallTraces().add("预约挂号(" + AppointmentMapper.traceSummary(state.getSlotMap()) + ") → " + result);

        long cost = System.currentTimeMillis() - start;
        state.getStepCostMs().put("BookAppointmentNode", cost);
        log.info("[Workflow][BookAppointmentNode] memoryId={} result={} hasBooked={} costMs={}",
                state.getMemoryId(), result, state.isHasBooked(), cost);
        // 把 save 结果挂到 state，供 ResponseAssembleNode 使用
        state.setFinalResponse(result);
        return state;
    }
}
