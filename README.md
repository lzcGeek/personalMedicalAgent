# 个人医疗助手

基于 LangChain4j 和 Spring Boot 的智能医疗助手应用，提供健康咨询、智能分诊、预约挂号等功能。

## 技术栈

### 后端
- **Spring Boot 3.2.6** - Web 应用框架
- **LangChain4j 1.0.0-beta3** - Java AI 开发框架
- **MyBatis-Plus 3.5.11** - ORM 持久层
- **MySQL** - 关系型数据库
- **MongoDB** - 文档数据库（对话记忆存储）

### 前端
- **Vue 3** + **Vite** - 前端框架
- **Element Plus** - UI 组件库
- **Axios** - HTTP 请求

### AI 模型支持
- 阿里云百炼 DashScope（通义千问）
- OpenAI 兼容接口（DeepSeek 等）
- Ollama 本地大模型

## 项目结构

```
├── src/main/java/           # 后端源码
│   ├── assistant/           # AI 助手接口
│   ├── bean/                # 数据对象
│   ├── config/              # Spring 配置
│   ├── controller/          # REST 控制器
│   ├── entity/              # 数据实体
│   ├── mapper/              # MyBatis 映射器
│   ├── service/             # 业务服务
│   ├── store/               # MongoDB 记忆存储
│   └── tools/               # AI 工具集
├── src/main/resources/      # 配置文件
├── xiaozhi-ui/              # 前端 Vue 项目
└── pom.xml
```

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+
- MongoDB 8.0+

### 1. 配置环境变量
```bash
set DASH_SCOPE_API_KEY=your-dashscope-api-key
```

### 2. 启动后端
```bash
cd java-ai-langchain4j
mvn spring-boot:run
```
后端启动在 `http://localhost:8080`，API 文档：`http://localhost:8080/doc.html`

### 3. 启动前端
```bash
cd xiaozhi-ui/xiaozhi-ui
npm install
npm run dev
```
前端启动在 `http://localhost:5173`

## 功能特性

- 智能健康咨询：基于大模型的医疗问答
- AI 分导诊：根据病情推荐科室
- 预约挂号：查询号源、预约、取消挂号
- 对话记忆：多轮对话上下文持久化（MongoDB）
- 历史会话：侧边栏展示历史对话，支持切换
- 流式输出：实时响应体验

## License

MIT
