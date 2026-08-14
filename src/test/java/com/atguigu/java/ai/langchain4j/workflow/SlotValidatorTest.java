package com.atguigu.java.ai.langchain4j.workflow;

import com.atguigu.java.ai.langchain4j.workflow.nodes.SlotValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 8 测试文件 1：槽位校验纯函数单测（无 Spring 容器，秒跑）。
 * 覆盖 TR-3.1 ~ TR-3.4：缺项、格式校验、日期格式、身份证号、完整情况。
 */
class SlotValidatorTest {

    // ========== 单一格式校验 ==========

    @Test @DisplayName("姓名：少于 2 字 → 报错")
    void nameTooShort() {
        assertNotNull(SlotValidator.validateFormat("name", "张"));
    }

    @Test @DisplayName("姓名：2 字以上 → OK")
    void nameOk() {
        assertNull(SlotValidator.validateFormat("name", "张三"));
    }

    @Test @DisplayName("身份证：18 位合法格式 → OK")
    void idCardOk() {
        // 随机合法格式的 18 位号码（不验真伪，只验格式）
        assertNull(SlotValidator.validateFormat("idCard", "110101199003071234"));
    }

    @Test @DisplayName("身份证：15 位旧格式 → 报错")
    void idCard15Fail() {
        assertNotNull(SlotValidator.validateFormat("idCard", "110101900307123"));
    }

    @Test @DisplayName("身份证：X 结尾 → OK")
    void idCardEndWithX() {
        assertNull(SlotValidator.validateFormat("idCard", "11010119900307123X"));
    }

    @Test @DisplayName("日期：正确格式 → OK")
    void dateOk() {
        assertNull(SlotValidator.validateFormat("date", "2025-04-14"));
    }

    @Test @DisplayName("日期：2025/04/14 斜杠格式 → 报错")
    void dateSlash() {
        assertNotNull(SlotValidator.validateFormat("date", "2025/04/14"));
    }

    @Test @DisplayName("日期：2025-02-30 非法日期 → 报错")
    void dateInvalid() {
        assertNotNull(SlotValidator.validateFormat("date", "2025-02-30"));
    }

    @Test @DisplayName("时间：上午/下午 → OK")
    void timeOk() {
        assertNull(SlotValidator.validateFormat("time", "上午"));
        assertNull(SlotValidator.validateFormat("time", "下午"));
    }

    @Test @DisplayName("时间：全天 → 报错")
    void timeWholeDay() {
        assertNotNull(SlotValidator.validateFormat("time", "全天"));
    }

    // ========== 整体 5 项齐全校验 ==========

    @Test @DisplayName("5 项齐全格式合法 → 空错误 Map")
    void fiveAllOk() {
        Map<String, String> m = fullOk();
        Map<String, String> errs = SlotValidator.checkAppointmentSlots(m);
        assertTrue(errs.isEmpty(), "应该无错误，实际：" + errs);
    }

    @Test @DisplayName("缺身份证 → 1 个错误")
    void missingIdCard() {
        Map<String, String> m = fullOk();
        m.remove("idCard");
        Map<String, String> errs = SlotValidator.checkAppointmentSlots(m);
        assertEquals(1, errs.size(), "应该 1 个错误，实际：" + errs);
        assertTrue(errs.containsKey("idCard"));
    }

    @Test @DisplayName("日期格式错 → 1 个错误，buildQuestion 含"日期"关键字")
    void dateWrongBuildQuestion() {
        Map<String, String> m = fullOk();
        m.put("date", "2025/04/14"); // 非 YYYY-MM-DD
        Map<String, String> errs = SlotValidator.checkAppointmentSlots(m);
        assertEquals(1, errs.size());
        String q = SlotValidator.buildQuestion(errs);
        assertNotNull(q);
        assertTrue(q.contains("日期"), "追问话术应包含"日期"：" + q);
    }

    @Test @DisplayName("缺 2 项（姓名+科室）→ 错误数=2，buildQuestion 只问第一个")
    void twoMissing() {
        Map<String, String> m = fullOk();
        m.remove("name");
        m.remove("department");
        Map<String, String> errs = SlotValidator.checkAppointmentSlots(m);
        assertEquals(2, errs.size());
        String q = SlotValidator.buildQuestion(errs);
        assertNotNull(q);
        // 不应出现两个"请问"
        long count = q.chars().filter(c -> c == '请').count();
        assertTrue(count == 1, "一次应只问第一个缺项，实际话术：" + q);
    }

    @Test @DisplayName("历史正则回填：combinedText 含身份证 → 自动写入 slotMap")
    void fillFromHistory() {
        Map<String, String> slotMap = new LinkedHashMap<>();
        String combined = "我叫王五，身份证 110101199205061234，想 2025-05-01 上午去";
        SlotValidator.fillFromHistoryRegex(combined, slotMap);
        assertEquals("110101199205061234", slotMap.get("idCard"));
        assertEquals("2025-05-01", slotMap.get("date"));
        assertEquals("上午", slotMap.get("time"));
    }

    @Test @DisplayName("正则回填：已有值不覆盖")
    void fillNoOverride() {
        Map<String, String> slotMap = new LinkedHashMap<>();
        slotMap.put("time", "下午");  // 之前是下午
        String combined = "我想 2025-05-01 上午去"; // 历史出现上午
        SlotValidator.fillFromHistoryRegex(combined, slotMap);
        assertEquals("下午", slotMap.get("time"), "已有值不应被历史文本覆盖");
        assertEquals("2025-05-01", slotMap.get("date"));
    }

    // ========== helper ==========
    private Map<String, String> fullOk() {
        Map<String, String> m = new HashMap<>();
        m.put("name", "张三");
        m.put("idCard", "110101199003071234");
        m.put("department", "神经内科");
        m.put("date", "2025-04-14");
        m.put("time", "下午");
        return m;
    }
}
