package com.atguigu.java.ai.langchain4j.workflow.router;

import com.atguigu.java.ai.langchain4j.workflow.state.Branch;
import com.atguigu.java.ai.langchain4j.workflow.state.XiaozhiWorkflowState;

/**
 * 工作流路由（纯函数，无任何外部依赖，单测零成本）。
 * 根据 State.branch 和各中间标志位，返回"下一组应该执行的节点链标识"字符串常量，
 * 由 XiaozhiWorkflowService 编排层做 switch-case 分发执行。
 *
 * 路由常量：
 *   AGENT_PATH            → 跳出工作流，走原 XiaozhiAgent（分诊/闲聊）
 *   AGENT_FALLBACK        → 跳出工作流，走 Fallback 降级（分类失败/异常）
 *   SLOT_QUESTION_RETURN  → 槽位缺项或需确认，直接返回追问话术给用户，不跑后续节点
 *   QUERY_CONFIRM_BOOK    → 挂号链路：查号源 → 确认校验 → 入库 → 组装响应
 *   CHECK_CONFIRM_CANCEL  → 取消链路：确认校验 → 执行取消 → 组装响应
 *   DIRECT_ASSEMBLE       → 跳过动作节点（号源已确认 false 场景等），直接组装响应
 */
public class IntentRouter {

    public static final String AGENT_PATH = "AGENT_PATH";
    public static final String AGENT_FALLBACK = "AGENT_FALLBACK";
    public static final String SLOT_QUESTION_RETURN = "SLOT_QUESTION_RETURN";
    public static final String QUERY_CONFIRM_BOOK = "QUERY_CONFIRM_BOOK";
    public static final String CHECK_CONFIRM_CANCEL = "CHECK_CONFIRM_CANCEL";
    public static final String DIRECT_ASSEMBLE = "DIRECT_ASSEMBLE";

    /**
     * 路由决策。
     * 注意：此方法不修改 State，只返回常量字符串。
     *
     * @return 节点链标识（见上方常量）
     */
    public String route(XiaozhiWorkflowState state) {
        Branch branch = state.getBranch();
        if (branch == null) branch = Branch.FALLBACK;

        return switch (branch) {
            case AGENT_RAG, AGENT_DIRECT -> AGENT_PATH;
            case FALLBACK -> AGENT_FALLBACK;

            case WORKFLOW_APPOINTMENT, WORKFLOW_CANCEL -> {
                // 槽位缺 → 返回追问话术（不跑后续节点）
                if (state.isNeedCollectSlots()) {
                    yield SLOT_QUESTION_RETURN;
                }

                if (branch == Branch.WORKFLOW_APPOINTMENT) {
                    // 号源 false：Router 直接跳到 DIRECT_ASSEMBLE（回复"暂无号源，是否考虑其他日期"）
                    if (state.getHasAvailability() != null && !state.getHasAvailability()) {
                        yield DIRECT_ASSEMBLE;
                    }
                    // 槽位齐全，号源 ok 或未知（QUERY_CONFIRM_BOOK 包含 QueryAvailability → Confirm → Book）
                    yield QUERY_CONFIRM_BOOK;
                } else { // CANCEL
                    yield CHECK_CONFIRM_CANCEL;
                }
            }
        };
    }
}
