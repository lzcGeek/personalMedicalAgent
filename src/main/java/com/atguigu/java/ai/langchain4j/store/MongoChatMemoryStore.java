package com.atguigu.java.ai.langchain4j.store;

import com.atguigu.java.ai.langchain4j.bean.ChatMessages;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

@Component
public class MongoChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MongoChatMemoryStore.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String memoryIdStr = String.valueOf(memoryId);
        log.info("getMessages called, memoryId={}", memoryIdStr);
        Criteria criteria = Criteria.where("memoryId").is(memoryIdStr);
        Query query = new Query(criteria);

        ChatMessages chatMessages = mongoTemplate.findOne(query, ChatMessages.class);
        if (chatMessages == null) {
            log.info("getMessages: no existing messages found for memoryId={}", memoryIdStr);
            return new LinkedList<>();
        }
        String contentJson = chatMessages.getContent();
        List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(contentJson);
        log.info("getMessages: found {} messages for memoryId={}", messages.size(), memoryIdStr);
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        String memoryIdStr = String.valueOf(memoryId);
        log.info("updateMessages called, memoryId={}, messageCount={}", memoryIdStr, list.size());
        Criteria criteria = Criteria.where("memoryId").is(memoryIdStr);
        Query query = new Query(criteria);
        Update update = new Update();

        update.set("memoryId", memoryIdStr);
        update.set("content", ChatMessageSerializer.messagesToJson(list));

        //修改或新增
        mongoTemplate.upsert(query, update, ChatMessages.class);
        log.info("updateMessages: upsert completed for memoryId={}", memoryIdStr);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String memoryIdStr = String.valueOf(memoryId);
        log.info("deleteMessages called, memoryId={}", memoryIdStr);
        Criteria criteria = Criteria.where("memoryId").is(memoryIdStr);
        Query query = new Query(criteria);
        mongoTemplate.remove(query, ChatMessages.class);
    }
}
