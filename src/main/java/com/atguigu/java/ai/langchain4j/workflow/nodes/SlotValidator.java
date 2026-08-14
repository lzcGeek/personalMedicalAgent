package com.atguigu.java.ai.langchain4j.workflow.nodes;

import com.atguigu.java.ai.langchain4j.workflow.state.SlotKeys;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 槽位校验纯函数工具类：无状态、无 Spring 依赖，可被 SlotCollectNode 和所有业务动作节点的前置断言复用。
 * 所有方法 static，JUnit 单测零外部依赖。
 */
public final class SlotValidator {

    private SlotValidator() {}

    /** 身份证号格式：17 位数字 + 第18位数字或 X/x（不联网验真伪，只做格式+校验位简单验证） */
    private static final Pattern ID_CARD_REGEX = Pattern.compile("^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$");
    /** 日期正则（YYYY-MM-DD），再额外用 DateTimeFormatter 真实校验 */
    private static final Pattern DATE_REGEX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 每个必填槽位对应的用户友好名称 */
    private static final Map<String, String> SLOT_LABELS = new LinkedHashMap<>();
    static {
        SLOT_LABELS.put(SlotKeys.SLOT_NAME, "患者姓名");
        SLOT_LABELS.put(SlotKeys.SLOT_IDCARD, "身份证号（18 位）");
        SLOT_LABELS.put(SlotKeys.SLOT_DEPARTMENT, "预约科室");
        SLOT_LABELS.put(SlotKeys.SLOT_DATE, "预约日期（格式 YYYY-MM-DD）");
        SLOT_LABELS.put(SlotKeys.SLOT_TIME, "预约时间段（上午 / 下午）");
    }

    /**
     * 校验单个槽位格式是否合法（不检查缺失）。
     *
     * @param key   槽位键，见 {@link SlotKeys}
     * @param value 槽位值（非空字符串）
     * @return null 表示合法；否则返回错误原因说明（用于追问话术）
     */
    public static String validateFormat(String key, String value) {
        if (value == null || value.isBlank()) return "未提供";
        String v = value.trim();
        return switch (key) {
            case SlotKeys.SLOT_NAME -> (v.length() < 2) ? "姓名至少 2 个字" : null;
            case SlotKeys.SLOT_IDCARD -> ID_CARD_REGEX.matcher(v).matches() ? null : "身份证号格式错误（需 18 位有效号码）";
            case SlotKeys.SLOT_DEPARTMENT -> (v.length() >= 2) ? null : "请填写具体科室名称";
            case SlotKeys.SLOT_DATE -> {
                if (!DATE_REGEX.matcher(v).matches()) {
                    yield "日期格式错误，必须是 YYYY-MM-DD（如 2025-04-14）";
                }
                try {
                    LocalDate.parse(v, DATE_FMT);
                    yield null;
                } catch (DateTimeParseException e) {
                    yield "日期不存在（如 2025-02-30），请核对";
                }
            }
            case SlotKeys.SLOT_TIME -> ("上午".equals(v) || "下午".equals(v)) ? null : "时间段只能选「上午」或「下午」";
            case SlotKeys.SLOT_DOCTOR -> null; // 医生名可选且无强格式
            default -> null;
        };
    }

    /**
     * 检查挂号 5 个必填槽位是否齐全 + 格式合法。
     *
     * @param slotMap 当前已提供槽位
     * @return 缺失/格式错误清单（key=槽位键, value=错误原因描述）；空 Map 表示一切 OK
     */
    public static Map<String, String> checkAppointmentSlots(Map<String, String> slotMap) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> safe = slotMap == null ? new HashMap<>() : slotMap;
        for (String k : SlotKeys.REQUIRED_APPOINTMENT_SLOTS) {
            String v = safe.get(k);
            String reason = validateFormat(k, v);
            if (reason != null) {
                errors.put(k, SLOT_LABELS.getOrDefault(k, k) + "：" + reason);
            }
        }
        return errors;
    }

    /**
     * 根据缺失错误清单生成用户追问话术（一次只问第一个缺项，避免一次性问太多用户反感）。
     *
     * @param errors 非空错误清单
     * @return 友好追问话术，形如 "请问您的身份证号（18 位）是？"
     */
    public static String buildQuestion(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) return null;
        Map.Entry<String, String> first = errors.entrySet().iterator().next();
        String label = SLOT_LABELS.getOrDefault(first.getKey(), first.getKey());
        // 格式错误 vs 纯缺失：话术略有区分
        String reason = first.getValue();
        if (reason.contains("未提供") || reason.contains("请填写具体科室")) {
            return "请问您的" + label + "是？";
        }
        return "刚才的" + label + "信息有点问题（" + reason + "），麻烦再提供一下正确的" + firstKeyName(first.getKey()) + "？";
    }

    private static String firstKeyName(String key) {
        return switch (key) {
            case SlotKeys.SLOT_NAME -> "姓名";
            case SlotKeys.SLOT_IDCARD -> "身份证号";
            case SlotKeys.SLOT_DEPARTMENT -> "科室";
            case SlotKeys.SLOT_DATE -> "预约日期";
            case SlotKeys.SLOT_TIME -> "时间段";
            default -> "信息";
        };
    }

    /**
     * 从历史对话 user/ai 交替文本里做简单正则槽位回填（供 SlotCollectNode 复用）。
     * 当前只做最基础识别：身份证号 / 上午下午时间 / YYYY-MM-DD。
     * 姓名和科室仍依赖 IntentClassifyNode 的 LLM 提取，准确率更高。
     *
     * @param combinedHistory 历史 UserMessage 拼接字符串（不含 Ai 大段回答）
     * @param existing        已有槽位（不覆盖已有值）
     */
    public static void fillFromHistoryRegex(String combinedHistory, Map<String, String> existing) {
        if (combinedHistory == null || existing == null) return;
        // 身份证号
        if (!existing.containsKey(SlotKeys.SLOT_IDCARD) || existing.get(SlotKeys.SLOT_IDCARD).isBlank()) {
            java.util.regex.Matcher m = ID_CARD_REGEX.matcher(combinedHistory);
            if (m.find()) existing.put(SlotKeys.SLOT_IDCARD, m.group());
        }
        // 日期 YYYY-MM-DD
        if (!existing.containsKey(SlotKeys.SLOT_DATE) || existing.get(SlotKeys.SLOT_DATE).isBlank()) {
            java.util.regex.Matcher m = DATE_REGEX.matcher(combinedHistory);
            if (m.find()) {
                String d = m.group();
                if (validateFormat(SlotKeys.SLOT_DATE, d) == null) {
                    existing.put(SlotKeys.SLOT_DATE, d);
                }
            }
        }
        // 上午/下午
        if (!existing.containsKey(SlotKeys.SLOT_TIME) || existing.get(SlotKeys.SLOT_TIME).isBlank()) {
            if (combinedHistory.contains("上午")) existing.put(SlotKeys.SLOT_TIME, "上午");
            else if (combinedHistory.contains("下午")) existing.put(SlotKeys.SLOT_TIME, "下午");
        }
    }
}
