package com.atguigu.java.ai.langchain4j.workflow.state;

/**
 * 工作流分支枚举：Router 路由后的执行路径标识
 */
public enum Branch {
    /** 进入挂号预约工作流链路 */
    WORKFLOW_APPOINTMENT,
    /** 进入取消预约工作流链路 */
    WORKFLOW_CANCEL,
    /** 跳出工作流，走原 Agent（RAG+分诊/挂号） */
    AGENT_RAG,
    /** 跳出工作流，走原 Agent（直接对话，不含 RAG 检索）*/
    AGENT_DIRECT,
    /** 工作流异常/识别失败，降级走原 Agent */
    FALLBACK
}
