package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.mapper.AppointmentMapper;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentServiceImpl extends ServiceImpl<AppointmentMapper, Appointment> implements AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentServiceImpl.class);

    /**
     * 按 5 项联合键查询是否存在重复预约（姓名+身份证+科室+日期+时段）
     */
    @Override
    @Transactional
    public Appointment getOne(Appointment appointment) {
        LambdaQueryWrapper<Appointment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Appointment::getUsername, appointment.getUsername());
        queryWrapper.eq(Appointment::getIdCard, appointment.getIdCard());
        queryWrapper.eq(Appointment::getDepartment, appointment.getDepartment());
        queryWrapper.eq(Appointment::getDate, appointment.getDate());
        queryWrapper.eq(Appointment::getTime, appointment.getTime());
        return baseMapper.selectOne(queryWrapper);
    }

    /**
     * 号源查询（TODO：占位实现，实际对接 HIS 排班系统）
     */
    @Override
    public boolean queryAvailability(String department, String date, String time, String doctorName) {
        log.info("[AppointmentService] queryAvailability: dept={}, date={}, time={}, doctor={}",
                department, date, time, doctorName);
        // TODO: 维护医生的排班信息：
        //  1) 未指定医生 -> 根据 科室+日期+时段 判断是否有排班医生可预约
        //  2) 指定医生   -> 判断该医生在该时段是否排班，以及剩余号源是否 >0
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String bookAppointment(Appointment appointment) {
        // 1. 查重（相同人 同时段 同科室）
        Appointment existing = getOne(appointment);
        if (existing != null) {
            log.info("[AppointmentService] bookAppointment 重复预约: user={}, dept={}, date={}, time={}",
                    maskName(appointment.getUsername()), appointment.getDepartment(), appointment.getDate(), appointment.getTime());
            return "您在相同的科室和时间已有预约";
        }
        // 2. 防幻视：清空 id（大模型可能幻觉带 id）
        appointment.setId(null);
        if (save(appointment)) {
            log.info("[AppointmentService] bookAppointment 入库成功: generatedId={}", appointment.getId());
            return "预约成功，并返回预约详情";
        }
        log.warn("[AppointmentService] bookAppointment 入库失败: user={}, dept={}",
                maskName(appointment.getUsername()), appointment.getDepartment());
        return "预约失败";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cancelAppointment(Appointment appointment) {
        Appointment existing = getOne(appointment);
        if (existing == null) {
            log.info("[AppointmentService] cancelAppointment 未找到记录: user={}, dept={}, date={}, time={}",
                    maskName(appointment.getUsername()), appointment.getDepartment(), appointment.getDate(), appointment.getTime());
            return "您没有预约记录，请核对预约科室和时间";
        }
        if (removeById(existing.getId())) {
            log.info("[AppointmentService] cancelAppointment 删除成功: id={}", existing.getId());
            return "取消预约成功";
        }
        log.warn("[AppointmentService] cancelAppointment 删除失败: id={}", existing.getId());
        return "取消预约失败";
    }

    /** 日志脱敏：姓名只保留首字 */
    private String maskName(String name) {
        if (name == null || name.isEmpty()) return "***";
        return name.length() <= 1 ? name + "**" : name.charAt(0) + "**";
    }
}