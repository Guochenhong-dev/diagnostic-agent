# Java应用故障诊断与修复验证系统

郭晨红 · 项目四 · Java / Agent 应用开发示例

这是可运行的学习与面试演示工程：输入 Java 错误日志，定位方法级源码证据，生成诊断与单文件修复建议，经人工确认后在 Docker 中对比修复前后的测试结果。代码为本次新建，不能直接视作既往公司生产成果。

## 1. 先用最简单的方式启动（Windows）

准备 **JDK 17、Maven 3.9+**。基础模式不需要 Python、Docker、数据库服务或模型密钥。首次构建需要联网下载 Maven 依赖。

1. 解压到 `D:\projects\diagnostic-agent`，确认这个目录直接包含 `pom.xml`、`src` 和 `projects`。不要进入某个故障样例的目录启动主服务。
2. 在该目录打开 PowerShell，执行：

```powershell
java -version
mvn -version
mvn clean test
mvn spring-boot:run
```

3. 浏览器打开 **http://127.0.0.1:8083**。访问令牌保留 `local-demo-token`，选择项目，使用页面的示例日志，点击创建诊断。
4. 查看根因、源码证据、修复建议和任务记录。默认是 **RULE_DEMO（规则演示）**，不是模型推理。没有启用 Docker 时，补丁验证按钮不可用，这是正常状态。
5. 终端按 `Ctrl+C` 停止服务。再次启动可以看到 H2 中保存的历史记录；正在执行的任务遇到重启会标记为 `INTERRUPTED`，需要重新创建。

IDEA 操作：打开根目录 `pom.xml` 为 Maven 工程；Project SDK 和 Maven Runner JRE 都选择 17；等待依赖下载。运行 `com.guo.diagnostic.Application`，运行配置的 Working directory 设为工程根目录。不要直接运行故障样例作为主服务。

也可以构建后运行：

```powershell
mvn clean package
java -jar target/diagnostic-agent.jar
```

Linux/macOS 使用相同 Maven 命令；设置环境变量时用 `export NAME=value` 替代 PowerShell 的 `$env:NAME="value"`。

## 2. 三个完整演示用例

| 项目标识 | 输入日志关键字 | 故障与修复 | 测试数量 |
|---|---|---|---|
| npe | NullPointerException、NameService.java:4 | `name.trim()` 遇到空值；按样例业务规则返回“匿名用户” | 3 |
| config | NumberFormatException、ConfigService.java:5 | 缺失 timeout 导致解析失败；仅缺失时使用 30，非法值仍报错 | 3 |
| sql | SQLException、UserMapper.java:5 | ResultSet 列名 userName 与实际 user_name 不一致 | 2 |

三个 `projects/*` 工程故意保留缺陷，其原始测试失败是预期结果。根工程的测试应通过。修复验证只修改 `work/` 中的复制目录，不覆盖原始项目，不修改测试断言。

## 3. 启用 Docker 修复验证

安装 Docker Desktop，启用 Linux containers，确保 `docker version` 能返回服务端信息。在根目录执行：

```powershell
docker build -f infra/runner.Dockerfile -t diagnostic-test-runner:1 .
$env:RUNNER_ENABLED="true"
mvn spring-boot:run
```

若主服务已启动，先停止，再在设置变量的同一 PowerShell 窗口重启。镜像构建需要联网，执行 Maven 预热，缓存编译器、JUnit、H2、Surefire 及其 provider；真正的验证容器禁网并运行离线 Maven。

网页创建一次新诊断，阅读补丁，勾选确认后点击验证。系统分别在两个工作副本运行 Maven：原始版本应存在测试失败；修复版本必须测试通过，且测试数量与原始版本一致、数量大于零，才返回 `VERIFIED`。其余结果为 `NOT_VERIFIED` 或执行错误，不能当作修复成功。

容器限制：禁网、只读根文件系统、删除 capabilities、no-new-privileges、CPU/内存/进程数量限制、超时终止。工作副本为可写挂载。默认镜像仅缓存自带样例所需依赖；新增项目需要重新准备其依赖，离线缺依赖会失败。

请只使用自己信任的本地项目。Maven 测试可以执行代码，Docker 资源限制不等于经过审计的恶意代码沙箱。工程目录不要带逗号，以免影响 Docker mount 参数；Windows 若挂载失败，检查 Docker Desktop 文件共享权限。

## 4. 接入真实大模型（可选）

应用使用 Spring AI 的 OpenAI 兼容接口。使用你实际开通的服务地址、密钥和模型名称，在启动服务的 PowerShell 窗口设置：

```powershell
$env:AI_ENABLED="true"
$env:AI_API_KEY="你的实际密钥"
$env:AI_BASE_URL="https://api.openai.com"
$env:AI_MODEL="你账户支持的模型名称"
mvn spring-boot:run
```

`AI_BASE_URL` 填服务根地址，默认由 Spring AI 拼接 `/v1/chat/completions`；不要重复添加 `/v1`。其他兼容厂商的路径以其实际 API 为准，必要时调整 `AiConfig.java`。

网页勾选使用模型后提交。调用会传送脱敏后的日志、方法代码和知识参考；仅在你允许源码发给该服务时开启。正则脱敏不是完整的数据防泄漏系统，请勿使用生产密钥和敏感日志。

`DiagnosisEngine` 使用结构化输出将结果映射为根因、证据 ID、建议和补丁；校验证据 ID 是否真实存在、补丁是否指向证据文件、替换锚点是否唯一。模型失败会明确记录失败，不会暗中切回规则模式。Token 为服务返回的 usage，不提供未经计算的费用估计。

## 5. MySQL 与 pgvector（可选，分别开启）

默认 H2 文件存储已足够演示。要切到 MySQL：

```powershell
docker compose up -d mysql
$env:DB_URL="jdbc:mysql://localhost:3307/diagnostics?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USER="diagnostics"
$env:DB_PASSWORD="diagnostics-local"
mvn spring-boot:run
```

等待 MySQL 初始化完成后启动主服务；表由 `schema.sql` 创建。H2 历史数据不会自动迁移到 MySQL。

要开启向量知识检索：

```powershell
docker compose up -d vector
$env:VECTOR_ENABLED="true"
$env:EMBEDDING_URL="https://api.openai.com/v1/embeddings"
$env:EMBEDDING_KEY="你的实际密钥"
$env:EMBEDDING_MODEL="text-embedding-3-small"
mvn spring-boot:run
```

保持该终端运行，在另一个 PowerShell 窗口创建内置知识的向量：

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8083/api/knowledge/index -Headers @{"X-App-Token"="local-demo-token"}
```

该示例只包含三条故障知识，使用 pgvector 余弦距离排序，不包含大规模知识库构建或 ANN 性能优化。更换 embedding 模型需清理并重新索引，保证查询与存储向量来自相同模型。初始化 SQL 仅在 PostgreSQL 数据卷首次创建时自动执行。默认向量连接为 localhost:5432、库 diagnostics、用户 diagnostics、密码 diagnostics-local，可通过 application.yml 中对应环境变量覆盖。

不开启向量功能时，返回三条固定参考知识，不称作语义检索。

## 6. Python MCP 工具适配器（可选）

安装 Python 3.10+，无额外 pip 依赖。先运行 Java 服务，再在支持 MCP stdio 的客户端配置：

```json
{
  "mcpServers": {
    "java-diagnostic": {
      "command": "python",
      "args": ["D:/projects/diagnostic-agent/mcp/server.py"],
      "env": {
        "DIAG_URL": "http://127.0.0.1:8083",
        "APP_TOKEN": "local-demo-token"
      }
    }
  }
}
```

提供 `list_projects`、`get_diagnosis`、`read_source` 三个只读工具。适配器使用标准输入输出传输 JSON-RPC；直接运行后等待输入不代表卡死。写入补丁与执行测试仍通过网页确认，不提供任意命令工具。

## 7. 项目结构和技术作用

| 路径 / 类 | 职责 |
|---|---|
| src/main/resources/static | 原生 HTML/CSS/JavaScript 页面，轮询任务、展示证据与补丁 |
| ApiController / ApiAuth | REST 接口、请求校验、X-App-Token 访问控制 |
| RunService / RunStore | 有界异步任务队列、状态流转、JDBC 持久化 |
| FilesService | 堆栈定位、JavaParser 方法提取、方法调用名称提取、文件路径约束 |
| DiagnosisEngine / AiConfig | 规则样例、Spring AI 结构化诊断、提示词与结果约束 |
| KnowledgeService | 内置知识、可选 Embedding 与 pgvector 查询 |
| DockerRunner | 复制工作区、应用单文件锚点补丁、受限容器运行测试、解析 Surefire XML |
| projects | 三个带真实 JUnit 测试的故障工程 |
| infra | 测试镜像、依赖预热、向量数据库初始化 |
| mcp/server.py | Python 实现的 MCP stdio 只读适配器 |
| src/test | 主服务工作流与边界测试 |

主流程：提交日志 → 提取源码证据 → 获取知识参考 → 生成并校验诊断 → 保存记录 → 人工确认 → 修复前后运行测试 → 展示验证结论。

状态：`QUEUED → ANALYZING → DIAGNOSED → VERIFYING → VERIFICATION_DONE`；失败分支为 `FAILED`、`VERIFY_FAILED`，重启中断为 `INTERRUPTED`。`VERIFICATION_DONE` 只表示执行完成，请继续查看 conclusion 是否为 `VERIFIED`。

## 8. HTTP 接口

所有 `/api` 接口要求请求头 `X-App-Token: local-demo-token`，可以用 `APP_TOKEN` 修改，网页输入保持一致。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | /api/config | 模型与验证功能是否开启 |
| GET | /api/projects | 注册项目列表 |
| GET | /api/source?project=npe&path=src/main/java/demo/NameService.java | 受约束的源码读取 |
| POST | /api/runs | 创建诊断，返回 id |
| GET | /api/runs | 历史记录 |
| GET | /api/runs/{id} | 单个任务详情 |
| POST | /api/runs/{id}/verify | 提交 `{ "confirmed": true }` 启动验证 |
| POST | /api/knowledge/index | 建立可选向量知识索引 |

创建诊断请求：

```json
{"project":"npe","log":"NullPointerException at demo.NameService.display(NameService.java:4)","useAi":false}
```

## 9. 添加自己的 Java 项目

复制到 `projects/你的项目标识/`，标识只用英文字母、数字、下划线或短横线，目录包含 pom.xml 和 src。当前支持普通单模块 Maven 工程，不完整支持多模块、Gradle、运行时动态生成代码。使用真实堆栈提交诊断，真实项目的推理建议需要开启模型。

最多扫描 200 个 Java 文件、单文件 256KB、最多 12 条证据；补丁仅修改一个 src/main/java 下的 Java 文件，按唯一锚点替换。JavaParser 提供语法级方法信息，不是完整跨项目调用图或符号解析。规则模式只识别三个内置样例，不是通用自动修复算法。

## 10. 验证、边界和排错

`mvn test` 检查鉴权、请求校验、三种故障的诊断与补丁、路径越界限制、禁止修改测试、模型未启用时报错、未知故障不捏造补丁。`validation.json` 保存交付时对三个复制样例执行本地 Maven 的前后对比结果。

交付环境没有 Docker 服务和模型密钥，因此 Docker 镜像/容器、真实模型、MySQL、pgvector 接入未在本环境完成端到端实测；相关代码与配置已提供。默认 H2 工作流和本地故障测试的通过，不代表这些外部接入已验证。

这是单用户本地演示，默认绑定 127.0.0.1，令牌和数据库密码为本地示例值。没有生产多租户、角色权限、分布式任务调度、自动合并 PR、完整审计系统或断点恢复。请不要直接公开部署。项目亮点适合讲解“代码证据约束、结构化输出、人工确认、测试前后对比”，不要写未经实测的生产准确率与节省成本。

常见问题：

- `mvn` 无法识别：安装 Maven 并把 bin 加入 PATH，重新打开终端；`mvn -version` 检查 Java 为 17。
- 找不到项目：Working directory 不对，回到包含 projects 的根目录；也可设置 PROJECTS_DIR 为绝对路径。
- 8083 被占用：设置 `$env:PORT="8084"` 后重启，浏览器和 MCP 地址同步修改。
- 401：网页令牌与 APP_TOKEN 不一致。
- Maven 下载失败：检查本机网络或 Maven settings.xml；不需要复制任何交付环境专用代理。
- 模型 401/404：检查密钥、模型权限与 base-url 拼接规则，重启服务使环境变量生效。
- 验证按钮不可用：确认 RUNNER_ENABLED=true、诊断生成了补丁并勾选确认。
- 测试容器报依赖缺失：重新构建 runner 镜像；自定义项目需要在构建阶段预热自己的全部测试依赖。
- 模型给出的补丁被拒绝：证据 ID/路径/锚点不满足约束，请人工检查，不能跳过校验。

参考文档：[Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)、[结构化输出](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html)、[JavaParser](https://javaparser.org/)。工程固定版本以 pom.xml 为准。
