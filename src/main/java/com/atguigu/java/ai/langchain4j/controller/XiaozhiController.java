package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.assistant.XiaozhiAgent;
import com.atguigu.java.ai.langchain4j.bean.ChatForm;
import com.atguigu.java.ai.langchain4j.bean.ChatMessages;
import com.atguigu.java.ai.langchain4j.workflow.service.XiaozhiWorkflowService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "个人医疗助手")
@RestController
@RequestMapping("/xiaozhi")
public class XiaozhiController {

    /**
     * 新的工作流入口：挂号/取消走显式工作流强约束 + 记忆白名单写入；
     * 分诊/闲聊/异常 Fallback 内部会委托给 xiaozhiAgent。
     */
    @Autowired
    private XiaozhiWorkflowService xiaozhiWorkflowService;

    /**
     * 保留原 XiaozhiAgent 以备：① 工作流内部 Fallback / Agent 路径使用；
     * ② 线上如出现紧急问题，可切回注释的兼容路径。
     */
    @SuppressWarnings("unused")
    @Autowired
    private XiaozhiAgent xiaozhiAgent;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Operation(summary = "获取历史会话列表")
    @GetMapping("/conversations")
    public List<String> getConversations() {
        List<String> result = new ArrayList<>();
        for (Document doc : mongoTemplate.getCollection("chat_messages").find()) {
            Object memId = doc.get("memoryId");
            if (memId != null) {
                result.add(String.valueOf(memId));
            }
        }
        return result.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    @Operation(summary = "获取会话历史消息")
    @GetMapping("/messages/{memoryId}")
    public List<Map<String, Object>> getMessages(@PathVariable String memoryId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Query query = new Query(Criteria.where("memoryId").is(memoryId));
        ChatMessages chatMessages = mongoTemplate.findOne(query, ChatMessages.class);
        if (chatMessages == null || chatMessages.getContent() == null) {
            return result;
        }
        List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(chatMessages.getContent());
        for (ChatMessage msg : messages) {
            Map<String, Object> map = new HashMap<>();
            map.put("type", msg.type().name());
            if (msg instanceof SystemMessage) {
                map.put("text", ((SystemMessage) msg).text());
            } else if (msg instanceof UserMessage) {
                map.put("text", ((UserMessage) msg).singleText());
            } else if (msg instanceof AiMessage) {
                map.put("text", ((AiMessage) msg).text());
            } else if (msg instanceof ToolExecutionResultMessage) {
                map.put("text", ((ToolExecutionResultMessage) msg).text());
            } else {
                map.put("text", msg.toString());
            }
            result.add(map);
        }
        return result;
    }

    @Operation(summary = "对话")
    @PostMapping(value = "/chat", produces = "text/stream;charset=utf-8")
    public Flux<String> chat(@RequestBody ChatForm chatForm) {
        // ===== 默认走新工作流 =====
        return xiaozhiWorkflowService.streamChat(chatForm.getMemoryId(), chatForm.getMessage());

        // ===== 兼容回退：如线上出问题，把上面一行注释，切回原 Agent 路径即可 =====
        // return xiaozhiAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());
    }
}
