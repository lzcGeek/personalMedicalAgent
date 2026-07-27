<template>
  <div class="app-layout">
    <div class="sidebar">
      <div class="logo-section">
        <img src="@/assets/logo.png" alt="个人医疗助手" width="80" height="80" />
        <span class="logo-text">个人医疗助手</span>
      </div>
      <el-button class="new-chat-button" @click="newChat">
        <i class="fa-solid fa-plus"></i>
        &nbsp;新会话
      </el-button>
      <!-- 历史会话列表 -->
      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv"
          :class="['conversation-item', { active: conv === uuid }]"
          @click="switchConversation(conv)"
        >
          <i class="fa-solid fa-message"></i>
          <span>会话 {{ conv }}</span>
        </div>
      </div>
    </div>
    <div class="main-content">
      <div class="chat-container">
        <div class="message-list" ref="messaggListRef">
          <div
            v-for="(message, index) in messages"
            :key="index"
            :class="
              message.isUser ? 'message user-message' : 'message bot-message'
            "
          >
            <i
              :class="
                message.isUser
                  ? 'fa-solid fa-user message-icon'
                  : 'fa-solid fa-robot message-icon'
              "
            ></i>
            <span>
              <span v-html="message.content"></span>
              <span
                class="loading-dots"
                v-if="message.isThinking || message.isTyping"
              >
                <span class="dot"></span>
                <span class="dot"></span>
              </span>
            </span>
          </div>
        </div>
        <div class="input-container">
          <el-input
            v-model="inputMessage"
            placeholder="请输入消息"
            @keyup.enter="sendMessage"
          ></el-input>
          <el-button @click="sendMessage" :disabled="isSending" type="primary"
            >发送</el-button
          >
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { v4 as uuidv4 } from 'uuid'

const messaggListRef = ref()
const isSending = ref(false)
const uuid = ref()
const inputMessage = ref('')
const messages = ref([])
const conversations = ref([])

onMounted(() => {
  initUUID()
  watch(messages, () => scrollToBottom(), { deep: true })
  loadConversations()
})

const loadConversations = async () => {
  try {
    const res = await axios.get('/api/xiaozhi/conversations')
    conversations.value = res.data
  } catch (e) {
    console.error('加载会话列表失败:', e)
  }
}

const scrollToBottom = () => {
  if (messaggListRef.value) {
    messaggListRef.value.scrollTop = messaggListRef.value.scrollHeight
  }
}

const sendMessage = () => {
  if (inputMessage.value.trim()) {
    sendRequest(inputMessage.value.trim())
    inputMessage.value = ''
  }
}

const sendRequest = (message) => {
  isSending.value = true
  const userMsg = {
    isUser: true,
    content: message,
    isTyping: false,
    isThinking: false,
  }
  if (messages.value.length > 0) {
    messages.value.push(userMsg)
  }

  const botMsg = {
    isUser: false,
    content: '',
    isTyping: true,
    isThinking: false,
  }
  messages.value.push(botMsg)
  const lastMsg = messages.value[messages.value.length - 1]
  scrollToBottom()

  axios
    .post(
      '/api/xiaozhi/chat',
      { memoryId: uuid.value, message },
      {
        responseType: 'stream',
        onDownloadProgress: (e) => {
          const fullText = e.event.target.responseText
          let newText = fullText.substring(lastMsg.content.length)
          lastMsg.content += newText
          scrollToBottom()
        },
      }
    )
    .then(() => {
      messages.value.at(-1).isTyping = false
      isSending.value = false
      // 刷新会话列表
      if (!conversations.value.includes(uuid.value.toString())) {
        conversations.value.unshift(uuid.value.toString())
      }
    })
    .catch((error) => {
      console.error('流式错误:', error)
      messages.value.at(-1).content = '请求失败，请重试'
      messages.value.at(-1).isTyping = false
      isSending.value = false
    })
}

const initUUID = () => {
  let storedUUID = localStorage.getItem('current_uuid')
  if (!storedUUID) {
    storedUUID = uuidToNumber(uuidv4())
    localStorage.setItem('current_uuid', storedUUID)
  }
  uuid.value = storedUUID
}

const uuidToNumber = (uuid) => {
  let number = 0
  for (let i = 0; i < uuid.length && i < 6; i++) {
    const hexValue = uuid[i]
    number = number * 16 + (parseInt(hexValue, 16) || 0)
  }
  return number % 1000000
}

const switchConversation = (convId) => {
  if (convId === uuid.value) return
  uuid.value = convId
  localStorage.setItem('current_uuid', convId)
  messages.value = []
}

const newChat = () => {
  const newId = uuidToNumber(uuidv4())
  uuid.value = newId
  localStorage.setItem('current_uuid', newId)
  messages.value = []
  if (!conversations.value.includes(newId.toString())) {
    conversations.value.unshift(newId.toString())
  }
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
}

.sidebar {
  width: 220px;
  background-color: #f4f4f9;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-section {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-text {
  font-size: 14px;
  font-weight: bold;
  margin-top: 6px;
}

.new-chat-button {
  width: 100%;
  margin-top: 16px;
}

.conversation-list {
  width: 100%;
  margin-top: 16px;
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #555;
  gap: 8px;
  transition: background-color 0.2s;
}

.conversation-item:hover {
  background-color: #e8e8f0;
}

.conversation-item.active {
  background-color: #d0d8f0;
  color: #333;
  font-weight: bold;
}

.main-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  background-color: #fff;
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
}

.message {
  margin-bottom: 10px;
  padding: 10px;
  border-radius: 4px;
  display: flex;
}

.user-message {
  max-width: 70%;
  background-color: #e1f5fe;
  align-self: flex-end;
  flex-direction: row-reverse;
}

.bot-message {
  max-width: 100%;
  background-color: #f1f8e9;
  align-self: flex-start;
}

.message-icon {
  margin: 0 10px;
  font-size: 1.2em;
}

.loading-dots {
  padding-left: 5px;
}

.dot {
  display: inline-block;
  margin-left: 5px;
  width: 8px;
  height: 8px;
  background-color: #000000;
  border-radius: 50%;
  animation: pulse 1.2s infinite ease-in-out both;
}

.dot:nth-child(2) {
  animation-delay: -0.6s;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  50% {
    transform: scale(1);
    opacity: 1;
  }
}

.input-container {
  display: flex;
}

.input-container .el-input {
  flex: 1;
  margin-right: 10px;
}

@media (max-width: 768px) {
  .main-content {
    padding: 10px 0 10px 0;
  }
  .app-layout {
    flex-direction: column;
  }
  .sidebar {
    width: 100%;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding: 10px;
  }
  .logo-section {
    flex-direction: row;
    align-items: center;
  }
  .logo-text {
    font-size: 16px;
  }
  .logo-section img {
    width: 32px;
    height: 32px;
  }
  .new-chat-button {
    margin-right: 30px;
    width: auto;
    margin-top: 5px;
  }
  .conversation-list {
    display: none;
  }
}

@media (min-width: 769px) {
  .main-content {
    padding: 0 0 10px 10px;
  }
  .app-layout {
    display: flex;
    height: 100vh;
  }
  .sidebar {
    width: 220px;
    background-color: #f4f4f9;
    padding: 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .logo-section {
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .logo-text {
    font-size: 14px;
    font-weight: bold;
    margin-top: 6px;
  }
  .new-chat-button {
    width: 100%;
    margin-top: 16px;
  }
}
</style>
