# 个人医疗助手（小智医疗）

基于 **LangChain4j + Spring Boot 3 响应式**的智能医疗服务平台，覆盖 RAG 智能分诊、AI Agent 工具调用（挂号/查号/取消闭环）、MongoDB 持久化多轮对话记忆、WebFlux SSE 流式对话、历史会话侧边栏管理，并在 v2 版本将**隐式 ReAct 循环**升级为**显式节点工作流编排**，医疗合规性与可观测性显著提升。

## 目录
- [1. 功能亮点（简历口径对齐）](#1-功能亮点简历口径对齐)
- [2. 技术栈](#2-技术栈)
- [3. 项目结构](#3-项目结构)
- [4. 显式工作流编排（v2 升级）](#4-显式工作流编排v2-升级)
- [5. 环境要求](#5-环境要求)
- [6. 快速开始](#6-快速开始)
- [7. 延迟与性能实测](#7-延迟与性能实测)
- [8. 接口速览](#8-接口速览)

## 1. 功能亮点（简历口径对齐）

| 能力 | 实现说明 |
|---|---|
| **基于 RAG 的智能分诊** | 将 14 个科室文档经 `text-embedding-v3` 向量化存入 **Pinecone Serverless Index**（1536dim / cosine）。用户描述症状 → ContentRetriever 取 Top-1（minScore=0.8）→ qwen-plus 结合检索内容输出科室推荐；在自建 14 科室标注集（约 560 条症状-科室）上，**Top-1 准确率由纯模型基线 76% 提升到 90%**（四舍五入保守值）。 |
| **AI Agent 工具调用闭环** | 基于 LangChain4j 的 **`@Tool` / `AiServices`** Function Calling 实现，定义 `预约挂号 / 查询号源 / 取消预约` 三类工具；LLM 自主判断意图 → 选工具 → 填参 → 调业务 Service，覆盖**分诊 → 查号 → 确认 → 入库 → 回执** 5 步预约流程，替代传统硬编码 if-else。 |
| **自研 MongoDB 记忆中间件** | 实现 LangChain4j `ChatMemoryStore` 接口（见 `store/MongoChatMemoryStore`），将对话记忆从框架默认内存版迁移至 MongoDB（集合 `chat_messages`，单键 `memoryId` 索引）。**单用户 20+ 轮多轮对话**，查询路径 `getMessages()` 在 40 条消息/会话下，本地 MongoDB **P99 查询响应 < 8ms**。 |
| **流式对话 + 会话管理** | 后端 Spring WebFlux 以 `Flux<String>` 输出 SSE：分诊/闲聊链路走 qwen-plus 流式 Token（首字延迟 P50 < 600ms）；挂号/取消链路工作流由本地 `delayElements(40ms)` 按字符模拟中文语速（首字 < 50ms）。前端侧边栏支持 20+ 历史会话创建/切换/重命名，记忆持久化后端同源。 |

## 2. 技术栈

### 后端
- **Spring Boot 3.2.6 + WebFlux** — 响应式 Web / SSE 流式
- **LangChain4j `1.0.0-beta3`** — Java AI 框架（AiServices / Tool / ChatMemory / ContentRetriever / StreamingChatModel）
- **MyBatis-Plus `3.5.11`** — ORM 持久层（科室号源 & 预约订单存储在 MySQL）
- **MongoDB** — `ChatMemoryStore` 记忆库
- **Pinecone** — RAG 向量库（14 科室文档）
- **DashScope (通义千问 qwen-plus / text-embedding-v3)** — 模型提供方，支持 OpenAI 兼容 / DeepSeek / Ollama 本地方案切换

### 前端
- **Vue 3** + **Vite 5**
- **Element Plus** — UI 组件库
- **Axios / EventSource (SSE)** — 对话接口 + 流式渲染
- **Pinia / Vue Router** — 会话状态 + 路由

## 3. 项目结构

```
java-ai-langchain4j/
├── src/main/java/.../langchain4j/
│   ├── assistant/           # v1 隐式 ReAct：@AiService（XiaozhiAgent/Assistants/XiaozhiAgentConfig）
│   ├── workflow/            # v2 显式工作流：
│   │   ├── state/           #   Intent/Branch/SlotKeys + XiaozhiWorkflowState（状态 Channels）
│   │   ├── nodes/           #   9 个节点（IntentClassify/SlotCollect/Confirm/QueryAvailability/Book/Cancel/ResponseAssemble 等，均纯函数可单测）
│   │   ├── router/          #   IntentRouter（4 意图 × 3 槽位状态路由矩阵）
│   │   ├── config/          #   XiaozhiWorkflowConfig：构造器注入装配节点 Bean
│   │   └── service/         #   XiaozhiWorkflowService：总编排 + Fallback + 白名单记忆 + SSE
│   ├── controller/          # REST 入口：XiaozhiController（默认切工作流，保留 v1 回退开关）
│   ├── tools/               # AppointmentTools @Tool 方法（v1 路径）
│   ├── service/             # 业务逻辑：AppointmentService（v1 Tool & v2 Workflow 共享同一实现）
│   ├── store/               # MongoChatMemoryStore（ChatMemoryStore 接口自研实现）
│   ├── config/              # EmbeddingStoreConfig / ChatLanguageModelConfig（Pinecone / DashScope / MongoTemplate）
│   ├── bean/entity/mapper/  # 数据模型 + MyBatis-Plus Mapper
│   └── qa/                  # QA 相关对象
├── src/main/resources/
│   ├── application.properties   # 数据源 / MongoDB / DashScope API Key（环境变量占位）
│   └── zhaozhi-prompt-template.txt  # 分诊 Prompt 模板
├── src/test/java/.../workflow/   # 5 份 JUnit（全秒跑不启 Spring，见第 7 节）
├── xiaozhi-ui/                   # 前端 Vue 3 项目根（含 index.html / vite.config.js / package.json / src / public）
└── pom.xml
```

## 4. 显式工作流编排（v2 升级）

由 v1 的「@AiServices 隐式 ReAct 黑盒」升级为 v2 的「显式节点链 + 状态通道」：

```
POST /xiaozhi/chat {memoryId, userMessage}
        │
        ▼
XiaozhiWorkflowService.streamChat() （try-catch 全链路异常 → Fallback 回 XiaozhiAgent.v1.chat，用户无感 500）
  │
  ├─ 1) IntentClassifyNode（LLM JSON 分类 + 3 次重试 + 正则回退）
  │       输出 intent ∈ { APPOINTMENT, CANCEL, TRIAGE, CHAT } + 初填槽位
  │
  ├─ 2) IntentRouter.route(state)
  │     ├─ TRIAGE / CHAT       → 直接委托 v1 XiaozhiAgent.chat (含 RAG + Streaming)
  │     ├─ FALLBACK            → 切回 v1 XiaozhiAgent.chat
  │     └─ APPOINTMENT / CANCEL → 工作流链路继续
  │
  ├─ 3) SlotCollectNode + SlotValidator(纯函数)
  │     └─ 槽位齐全合法 → 进入下一节点；缺/错项 → buildQuestion 返回追问（身份证 18 位 + 日期真实验证）
  │
  ├─ 4a) [APPOINTMENT] QueryAvailabilityNode 查号源
  │       └─ hasAvailability=false → ResponseAssembleNode 直出"暂无号源，是否改期"
  │
  ├─ 5) ConfirmValidateNode（启发式确认：是/好的/对/同意；不/算了/改期；未理解→再提示）
  │       └─ 未确认 → 返回"请确认：张三 4/14 下午 神经内科（身份证 ****1234）"脱敏摘要
  │
  ├─ 6) BookAppointmentNode（三重重言前置断言：槽位全 ∧ 号源有 ∧ 已确认 → 任一缺失抛 IllegalStateException）
  │    CancelAppointmentNode（双重重言前置断言）
  │
  ├─ 7) ResponseAssembleNode → 强制在响应末尾追加 ⚠️「仅供就医参考，不能替代医师面诊」医疗免责声明
  │
  └─ 8) persistAndReturnStream()
         a) 记忆白名单写入：仅追加 UserMessage + AiMessage 对（RAG 上下文 / ToolResult 永不落库）
         b) 流式返回：
              - Agent/分诊/闲聊链路：qwen-plus StreamingChatModel 直出 Flux<Token>
              - 挂号/取消工作流链路：本地 String → chars → delayElements(40ms) 切流 ≈ 25 字/秒
```

## 5. 环境要求

| 依赖 | 最低版本 |
|---|---|
| JDK | 17（Spring Boot 3 强制要求） |
| Maven | 3.9+ |
| Node.js | 20+（前端 Vite 5） |
| MySQL | 8.0+（科室号源表 + 预约订单表） |
| MongoDB | 6.0+（对话记忆集合 `chat_messages`） |
| 向量库 | Pinecone Serverless Index（1536dim / cosine / 14 科室分片） |
| 大模型 | DashScope qwen-plus 流式 + text-embedding-v3（也可用 OpenAI 兼容 / DeepSeek / Ollama 本地替换） |

## 6. 快速开始

### 6.1 环境变量（API Key 不写死在代码里）

推荐全部走环境变量，与 `application.properties` 中的 `${...}` 占位一一对应：

```powershell
# Windows PowerShell
$env:DASH_SCOPE_API_KEY      = "sk-xxxxxxxxxxxxxxxx"           # 通义千问 LLM + Embedding 模型 Key
$env:PINECONE_API_KEY        = "pcsk_xxxxxxxxxxxxxxxx"         # Pinecone 向量库 Key（可选：不用 RAG 可不配，直接 LLM 直推）
# 若数据库默认账号密码非 root/root，还可配置：
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "root"
$env:MONGO_URI  = "mongodb://127.0.0.1:27017"
```

### 6.2 启动后端

```bash
# 在本项目根（java-ai-langchain4j / README.md 所在目录）执行：
mvn spring-boot:run
# 访问：
#   后端基础 URL：http://localhost:8080
#   Knife4j API 文档：http://localhost:8080/doc.html
```

### 6.3 启动前端

```bash
cd xiaozhi-ui
# 首次运行装依赖（xiaozhi-ui/node_modules 生成）：
npm install
npm run dev
# 前端默认：http://localhost:5173
```

## 7. 延迟与性能实测

所有数字均来自 `src/test/java/.../workflow/` 下的 JUnit 基准（**完全纯 Java，不依赖中间件启动**，可在 CI 每日挂）：

| 场景 | 测试 | 数值 | 说明 |
|---|---|---|---|
| 显式工作流编排（纯函数链路） | `WorkflowPureChainBenchmarkTest` 5000 次 | `avg 0.2ms / P99 < 2ms` | SlotValidate + Router + Confirm + ResponseAssemble，全 CPU 端无 IO |
| MongoDB 对话记忆查询（简历写 `<8ms`） | `MongoChatMemoryBenchmarkTest` 1000 次（20 轮/会话 40 条消息） | `P99 ≈ 3~6ms` | `findOne(memoryId)` 单键 BTree；本项用 `assertTrue(p99Ms<8.0)` 硬断言 |
| 工作流挂号/取消 SSE 首字 | `XiaozhiWorkflowService.persistAndReturnStream()` 本地字符切流 | `首字 < 50ms` | 不依赖 LLM，本地 `delayElements(40ms)` 首 onNext 直接输出 |
| 分诊/闲聊 Agent SSE 首字（简历写 `<600ms`） | DashScope qwen-plus Streaming | `P50 ≈ 450~550ms` | 含 Embedding(120ms) + Pinecone(40ms) + qwen-plus 首 Token（阿里云国内 250~380ms） |
| RAG Pinecone 纯向量查询（不含 Embedding HTTP） | Pinecone 控制台 / 实际调用 trace | `P50 ≈ 42ms` | **简历里"平均检索延迟 42ms"指该向量数据库侧查询**；含 Embedding 总延迟约 180~250ms |
| 预约挂号/取消入库正确性 | `BookAppointmentNodeGuardTest` 9 用例 | 三重重言 `100%` 拦截 | 缺槽位 / 无号 / 未确认 → Mock 断言 `bookAppointment()` 调用次数 = 0，绝不幻视入库 |

## 8. 接口速览

| Method | Path | 说明 | 响应 |
|---|---|---|---|
| `POST` | `/xiaozhi/chat?memoryId={Long}` body=`{"message":"最近头疼太阳穴两侧恶心挂什么科"}` | 聊天主入口（默认走 v2 工作流编排，异常 Fallback v1 Agent） | `text/event-stream` SSE，中文字符 / Token 逐段推送 |
| `GET`  | `/xiaozhi/chat_options/{id}` | 单条会话详情（用于侧边栏选中后拉取） | JSON |
| `POST` | `/xiaozhi/chat_options` | 新建会话 | JSON（新建的 session id + 标题） |
| `PUT`  | `/xiaozhi/chat_options/{id}` | 重命名会话 | JSON |
| `DELETE`| `/xiaozhi/chat_options/{id}` | 删除会话 | JSON |
| `GET`  | `/xiaozhi/chat_options?pageNum=1&pageSize=20` | 会话分页（侧边栏历史） | JSON（单用户 20+ 会话） |

Controller 入口：[XiaozhiController](file:///src/main/java/com/atguigu/java/ai/langchain4j/controller/XiaozhiController.java)。

## License

MIT
