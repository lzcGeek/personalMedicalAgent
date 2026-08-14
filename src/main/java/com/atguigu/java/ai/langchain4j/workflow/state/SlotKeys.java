package com.atguigu.java.ai.langchain4j.workflow.state;

/**
 * 槽位字段常量：工作流挂号/取消场景收集的用户实名信息字段名
 */
public final class SlotKeys {

    private SlotKeys() {}

    /** 患者姓名（必填） */
    public static final String SLOT_NAME = "name";
    /** 身份证号（必填，18 位，脱敏存储） */
    public static final String SLOT_IDCARD = "idCard";
    /** 预约科室（必填） */
    public static final String SLOT_DEPARTMENT = "department";
    /** 预约日期（必填，格式 YYYY-MM-DD） */
    public static final String SLOT_DATE = "date";
    /** 预约时间段（必填，上午/下午） */
    public static final String SLOT_TIME = "time";
    /** 预约医生（可选） */
    public static final String SLOT_DOCTOR = "doctorName";

    /** 挂号链路必填槽位清单（5 项） */
    public static final String[] REQUIRED_APPOINTMENT_SLOTS = {
            SLOT_NAME, SLOT_IDCARD, SLOT_DEPARTMENT, SLOT_DATE, SLOT_TIME
    };
}
