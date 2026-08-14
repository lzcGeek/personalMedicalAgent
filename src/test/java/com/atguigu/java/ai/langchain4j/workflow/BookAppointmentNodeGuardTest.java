package com.atguigu.java.ai.langchain4j.workflow;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.workflow.nodes.BookAppointmentNode;
import com.atguigu.java.ai.langchain4j.workflow.nodes.QueryAvailabilityNode;
import com.atguigu.java.ai.langchain4j.workflow.state.Intent;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Task 8 测试文件 3：业务动作节点三重重言（前置断言拦截）测试。
 * 覆盖 AC-3 / TR-4.2：缺槽位、无号源、未确认 → 抛 IllegalStateException，业务方法 0 次调用
 */
class BookAppointmentNodeGuardTest {

    private AppointmentService mockService;
    private QueryAvailabilityNode queryNode;
    private BookAppointmentNode bookNode;

    @BeforeEach
    void setup() {
        mockService = Mockito.mock(AppointmentService.class);
        queryNode = new QueryAvailabilityNode(mockService);
        bookNode = new BookAppointmentNode(mockService);
        when(mockService.queryAvailability(anyString(), anyString(), anyString(), any()))
                .thenReturn(true);
        when(mockService.bookAppointment(any(Appointment.class)))
                .thenReturn("预约成功，并返回预约详情");
    }

    // ================== QueryAvailabilityNode 守卫 ==================

    @Test @DisplayName("QueryNode：槽位缺（needCollectSlots=true）→ 抛 IllegalStateException，queryAvailability 0 次")
    void querySlotsMissing() {
        XiaozhiWorkflowState s = base();
        s.setNeedCollectSlots(true); // 关键：前置条件
        assertThrows(IllegalStateException.class, () -> queryNode.apply(s));
        verify(mockService, times(0)).queryAvailability(any(), any(), any(), any());
    }

    @Test @DisplayName("QueryNode：非 APPOINTMENT/CANCEL → 抛 IllegalStateException")
    void queryWrongIntent() {
        XiaozhiWorkflowState s = base();
        s.setIntent(Intent.TRIAGE); // 非业务意图
        assertThrows(IllegalStateException.class, () -> queryNode.apply(s));
    }

    @Test @DisplayName("QueryNode：一切正常 → 调用 queryAvailability 1 次，写入 hasAvailability")
    void queryOk() {
        XiaozhiWorkflowState s = base();
        s = queryNode.apply(s);
        assertTrue(s.getHasAvailability());
        verify(mockService, times(1)).queryAvailability(
                eq("神经内科"), eq("2025-04-14"), eq("下午"), eq(null));
    }

    // ================== BookAppointmentNode 三重重言 ==================

    @Test @DisplayName("BookNode：needCollectSlots=true → 抛 IllegalStateException，bookAppointment 0 次")
    void bookSlotsMissing() {
        XiaozhiWorkflowState s = base();
        s.setNeedCollectSlots(true);
        s.setHasAvailability(true);
        s.setHasUserConfirmation(true);
        assertThrows(IllegalStateException.class, () -> bookNode.apply(s));
        verify(mockService, times(0)).bookAppointment(any());
    }

    @Test @DisplayName("BookNode：号源 false → 抛 IllegalStateException，bookAppointment 0 次")
    void bookNoAvailability() {
        XiaozhiWorkflowState s = base();
        s.setNeedCollectSlots(false);
        s.setHasAvailability(false); // 关键：无号源
        s.setHasUserConfirmation(true);
        assertThrows(IllegalStateException.class, () -> bookNode.apply(s));
        verify(mockService, times(0)).bookAppointment(any());
    }

    @Test @DisplayName("BookNode：未确认 → 抛 IllegalStateException，bookAppointment 0 次")
    void bookNotConfirmed() {
        XiaozhiWorkflowState s = base();
        s.setNeedCollectSlots(false);
        s.setHasAvailability(true);
        s.setHasUserConfirmation(false); // 关键：未确认
        assertThrows(IllegalStateException.class, () -> bookNode.apply(s));
        verify(mockService, times(0)).bookAppointment(any());
    }

    @Test @DisplayName("BookNode：三重条件全满足 → bookAppointment 调 1 次，hasBooked=true")
    void bookOk() {
        XiaozhiWorkflowState s = base();
        s.setNeedCollectSlots(false);
        s.setHasAvailability(true);
        s.setHasUserConfirmation(true);
        s = bookNode.apply(s);
        assertTrue(s.isHasBooked());
        verify(mockService, times(1)).bookAppointment(any());
        // 轨迹日志包含"预约挂号"
        assertTrue(s.getToolCallTraces().toString().contains("预约挂号"));
    }

    // ====== helper ======
    private XiaozhiWorkflowState base() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("name", "测试用户");
        slots.put("idCard", "110101199001011234");
        slots.put("department", "神经内科");
        slots.put("date", "2025-04-14");
        slots.put("time", "下午");
        return XiaozhiWorkflowState.builder()
                .memoryId(888L)
                .userMessage("测试")
                .intent(Intent.APPOINTMENT)
                .slotMap(slots)
                .build();
    }
}
