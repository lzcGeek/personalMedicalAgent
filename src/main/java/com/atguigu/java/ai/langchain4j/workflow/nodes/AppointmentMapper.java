package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.workflow.state.SlotKeys;

import java.util.Map;

/**
 * 工作流 槽位 → Appointment 实体转换工具（纯函数，无状态）。
 * 统一映射关系（避免 3 个业务节点各自写一遍，造成字段名漂移 bug）。
 * 槽位键 → Appointment 字段：
 *   SLOT_NAME       → username
 *   SLOT_IDCARD     → idCard
 *   SLOT_DEPARTMENT → department
 *   SLOT_DATE       → date
 *   SLOT_TIME       → time
 *   SLOT_DOCTOR     → doctorName
 */
public final class AppointmentMapper {

    private AppointmentMapper() {}

    /** 把 slotMap（工作流中间态）转 Appointment 实体（id 留空：由 Service 层在 save 前清 0 防幻视） */
    public static Appointment fromSlotMap(Map<String, String> slotMap) {
        Appointment a = new Appointment();
        if (slotMap == null) return a;
        a.setUsername(str(slotMap.get(SlotKeys.SLOT_NAME)));
        a.setIdCard(str(slotMap.get(SlotKeys.SLOT_IDCARD)));
        a.setDepartment(str(slotMap.get(SlotKeys.SLOT_DEPARTMENT)));
        a.setDate(str(slotMap.get(SlotKeys.SLOT_DATE)));
        a.setTime(str(slotMap.get(SlotKeys.SLOT_TIME)));
        a.setDoctorName(str(slotMap.get(SlotKeys.SLOT_DOCTOR)));
        return a;
    }

    /** 身份证号脱敏：前 6 + **** + 后 4（工具轨迹/日志使用） */
    public static String maskIdCard(String id) {
        if (id == null || id.length() < 10) return "***";
        return id.substring(0, 6) + "****" + id.substring(id.length() - 4);
    }

    /** 脱敏槽位摘要字符串（用于 toolCallTraces），如 "name=张三,idCard=1101****1234,dept=神经内科" */
    public static String traceSummary(Map<String, String> slotMap) {
        if (slotMap == null) return "slots=empty";
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(v(slotMap.get(SlotKeys.SLOT_NAME))).append(',');
        sb.append("idCard=").append(maskIdCard(slotMap.get(SlotKeys.SLOT_IDCARD))).append(',');
        sb.append("dept=").append(v(slotMap.get(SlotKeys.SLOT_DEPARTMENT))).append(',');
        sb.append("date=").append(v(slotMap.get(SlotKeys.SLOT_DATE))).append(',');
        sb.append("time=").append(v(slotMap.get(SlotKeys.SLOT_TIME)));
        String doc = slotMap.get(SlotKeys.SLOT_DOCTOR);
        if (doc != null && !doc.isBlank()) sb.append(",doctor=").append(doc);
        return sb.toString();
    }

    private static String str(String s) { return s == null ? "" : s.trim(); }
    private static String v(String s) { return s == null || s.isBlank() ? "(未提供)" : s; }
}
