package com.atguigu.java.ai.langchain4j.workflow.state;

/**
 * 工作流意图枚举：首节点意图分类结果
 */
public enum Intent {
    /** 挂号预约 */
    APPOINTMENT,
    /** 取消预约 */
    CANCEL,
    /** 分诊咨询（走RAG+原Agent） */
    TRIAGE,
    /** 闲聊/其他（走原Agent直接对话） */
    CHAT
}
