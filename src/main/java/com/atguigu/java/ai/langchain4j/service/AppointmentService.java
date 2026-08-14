package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 挂号预约业务服务层。
 * 原 Agent 路径（AppointmentTools 的 @Tool 方法）和工作流路径（3 个业务节点）共享同一套实现，
 * 避免两套逻辑分叉造成行为不一致。
 */
public interface AppointmentService extends IService<Appointment> {

    /**
     * 按"姓名 + 身份证号 + 科室 + 日期 + 时间段"联合查询预约记录是否存在
     *
     * @param appointment 条件对象（用户名/身份证/科室/日期/时间必填；id 忽略）
     * @return 存在则返回完整 Appointment 记录（含自增 id）；不存在返回 null
     */
    Appointment getOne(Appointment appointment);

    /**
     * 查询指定条件下是否有号源（原 AppointmentTools.queryDepartment 逻辑下沉）。
     * 当前实现仍为 TODO 占位：真实业务场景需要对接医院 HIS 系统/排班表，判断：
     * ① 指定医生是否排班；② 排班时间段是否约满。
     * 占位实现恒返回 true 以保持与旧代码一致。
     *
     * @param department 科室名称
     * @param date       日期（YYYY-MM-DD）
     * @param time       上午 / 下午
     * @param doctorName 医生姓名（可选 null 或空串）
     * @return 是否有号源
     */
    boolean queryAvailability(String department, String date, String time, String doctorName);

    /**
     * 预约挂号（原 AppointmentTools.bookAppointment 逻辑下沉）。
     * 步骤：① 先用联合条件查重复；② 无重复则清空 id 防幻视后 save 入库。
     *
     * @param appointment 预约信息实体
     * @return 结果话术："预约成功，并返回预约详情" / "您在相同的科室和时间已有预约" / "预约失败"
     */
    String bookAppointment(Appointment appointment);

    /**
     * 取消预约挂号（原 AppointmentTools.cancelAppointment 逻辑下沉）。
     * 步骤：① 先联合查存在；② 用自增 id 删除；③ 根据结果返回话术。
     *
     * @param appointment 条件实体
     * @return "取消预约成功" / "您没有预约记录，请核对预约科室和时间" / "取消预约失败"
     */
    String cancelAppointment(Appointment appointment);
}