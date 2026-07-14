# 小智医疗 - Java AI LangChain4j

基于 LangChain4j 和 Spring Boot 的智能医疗助手应用。

## 技术栈

### 核心框架
- **Spring Boot 3.2.6** - Web 应用框架
- **LangChain4j 1.0.0-beta3** - Java AI 开发框架

### AI 模型支持
- OpenAI (GPT 系列)
- Ollama (本地大模型)
- 阿里云百炼平台 (DashScope)

### 数据存储
- **MySQL** - 关系型数据存储
- **MongoDB** - 文档数据库
- **MyBatis-Plus** - ORM 持久层框架

### 高级功能
- **RAG (检索增强生成)** - 基于文档的智能问答
- **流式输出** - WebFlux 响应式流
- **工具调用 (Tools)** - 动态执行外部功能

## 项目结构

```
src/
├── main/
│   ├── java/com/atguigu/java/ai/langchain4j/
│   │   ├── XiaozhiApp.java          # 应用入口
│   │   ├── assistant/               # AI 助手实现
│   │   │   ├── Assistant.java
│   │   │   ├── MemoryChatAssistant.java
│   │   │   ├── SeparateChatAssistant.java
│   │   │   └── XiaozhiAgent.java
│   │   ├── bean/                    # 数据对象
│   │   ├── config/                  # 配置类
│   │   ├── controller/              # Web 控制器
│   │   ├── entity/                  # 实体类
│   │   ├── mapper/                  # MyBatis 映射器
│   │   ├── service/                 # 业务服务
│   │   ├── store/                   # 存储实现
│   │   └── tools/                   # AI 工具集
│   │       ├── AppointmentTools.java  # 预约工具
│   │       └── CalculatorTools.java    # 计算器工具
│   └── resources/
│       └── application.properties   # 应用配置
└── test/                            # 测试用例
```

## 功能特性

### 🤖 智能对话
- 支持多种大语言模型
- 上下文记忆功能
- 流式响应输出

### 📋 预约管理
- 医疗预约创建与查询
- 预约状态管理

### 📄 文档理解
- PDF 文档解析
- RAG 智能问答

### 🔧 工具调用
- 计算器工具
- 自定义业务工具

## 配置说明

在 `application.properties` 中配置您的 API 密钥：

```properties
# OpenAI 配置
langchain4j.open-ai.api-key=your-api-key

# 阿里云百炼配置
langchain4j.dashscope.api-key=your-api-key

# MongoDB 配置
spring.data.mongodb.uri=mongodb://localhost:27017/xiaozhi

# MySQL 配置
spring.datasource.url=jdbc:mysql://localhost:3306/xiaozhi
spring.datasource.username=root
spring.datasource.password=your-password
```

## 运行项目

```bash
# 编译项目
mvn clean package

# 运行应用
mvn spring-boot:run

# 运行测试
mvn test
```

## API 文档

启动应用后访问 Knife4j 文档界面：
```
http://localhost:8080/doc.html
```

## License

MIT License
