# 家庭资产工程基础实施计划

> **致智能体工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实施本计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 创建一个具备生产形态的家庭资产仓库，运行 Java 21/Spring Boot 4 模块化单体应用和 Vue 3 前端，连接 PostgreSQL，通过 Docker Compose 启动，并通过 JVM、AOT 和 GraalVM 原生冒烟测试。

**架构：** 后端是一个 Spring Boot 应用，划分为八个 Spring Modulith 包。阶段一只包含基础设施和显式模块外壳；业务实体留给后续计划。前端是一个独立构建的 Vue 应用，在部署中由 Nginx 提供服务，`/api` 反向代理到原生后端。

**技术栈：** Java 21 LTS、Spring Boot 4.0.3、Spring Modulith 2.0.5、Maven 3.9.x、Flyway、PostgreSQL 17、Testcontainers、GraalVM Native Build Tools、Vue 3.5.20、TypeScript 5.9.3、Vite 8.0.16、Element Plus 2.11.0、Vitest、Docker Compose

---

## 范围边界

本计划仅建立可执行的基础。它刻意不实现用户、会话、分类、位置、物品记录、库存、通知、附件、PWA 离线行为或业务 API。这些属于后续路线图阶段。

## 目标文件映射

```text
stocket/
├── .github/workflows/ci.yml                    # JVM、前端、AOT、原生测试 CI
├── .env.example                                # 非密钥部署契约
├── Makefile                                    # 稳定的开发者入口
├── README.md                                   # 设置和验证命令
├── backend/
│   ├── .mvn/wrapper/maven-wrapper.properties   # Maven 包装器版本
│   ├── mvnw, mvnw.cmd                          # 可复现的 Maven 入口
│   ├── pom.xml                                 # Java、Boot、Modulith、原生依赖
│   └── src/
│       ├── main/java/com/familyassets/
│       │   ├── StocketApplication.java    # 应用程序和 @Modulithic 根
│       │   ├── system/                         # 仅基础的系统 API
│       │   ├── identity/package-info.java      # 模块声明
│       │   ├── catalog/package-info.java
│       │   ├── location/package-info.java
│       │   ├── inventory/package-info.java
│       │   ├── reminder/package-info.java
│       │   ├── notification/package-info.java
│       │   ├── attachment/package-info.java
│       │   └── audit/package-info.java
│       ├── main/resources/
│       │   ├── application.yml                 # 通用类型化配置
│       │   ├── application-local.yml           # 本地开发者默认配置
│       │   └── db/migration/V1__baseline.sql   # 扩展和模式标记
│       └── test/java/com/familyassets/
│           ├── StocketApplicationTests.java
│           ├── ArchitectureTest.java
│           ├── DatabaseMigrationTest.java
│           └── system/SystemApiTest.java
├── frontend/
│   ├── package.json, package-lock.json          # 固定的 JS 依赖图
│   ├── index.html, tsconfig*.json, vite.config.ts
│   └── src/
│       ├── App.vue                             # 响应式外壳
│       ├── main.ts                             # Vue/Element 启动引导
│       ├── api/system.ts                       # 类型化系统 API 客户端
│       ├── styles/main.css                     # 移动优先全局样式
│       └── test/App.spec.ts                    # 外壳/API 状态测试
└── deploy/
    ├── compose.yml                             # 网关、原生应用、PostgreSQL
    ├── app/Dockerfile                          # 原生构建和运行时镜像
    ├── frontend/Dockerfile                     # Vite 构建和 Nginx 镜像
    └── gateway/default.conf                    # SPA 回退和 API 代理
```

### 任务一：引导 Java 21 Maven 应用程序

**文件：**
- 创建：`backend/pom.xml`
- 创建：`backend/.mvn/wrapper/maven-wrapper.properties`
- 创建：`backend/mvnw`
- 创建：`backend/mvnw.cmd`
- 创建：`backend/src/main/java/com/familyassets/StocketApplication.java`
- 创建：`backend/src/test/java/com/familyassets/StocketApplicationTests.java`

- [ ] **步骤 1：添加 Maven 构建描述符**

创建 `backend/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.3</version>
    <relativePath/>
  </parent>

  <groupId>com.familyassets</groupId>
  <artifactId>stocket-backend</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>stocket-backend</name>
  <description>Stocket modular monolith backend</description>

  <properties>
    <java.version>21</java.version>
    <spring-modulith.version>2.0.5</spring-modulith.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-bom</artifactId>
        <version>${spring-modulith.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.modulith</groupId>
      <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-flyway</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.modulith</groupId>
      <artifactId>spring-modulith-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-testcontainers</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <executions>
          <execution>
            <goals>
              <goal>build-info</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.graalvm.buildtools</groupId>
        <artifactId>native-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **步骤 2：生成 Maven 包装器**

在 `backend/` 目录下运行：

```bash
mvn -N wrapper:wrapper -Dmaven=3.9.12
```

预期：`mvnw`、`mvnw.cmd` 和 `.mvn/wrapper/maven-wrapper.properties` 文件存在；属性文件指向 Maven 3.9.12。

- [ ] **步骤 3：编写失败的应用程序根测试**

创建 `backend/src/test/java/com/familyassets/StocketApplicationTests.java`：

```java
package com.familyassets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

class StocketApplicationTests {

    @Test
    void applicationRootDeclaresBootAndModulith() {
        assertThat(StocketApplication.class)
            .hasAnnotation(SpringBootApplication.class)
            .hasAnnotation(Modulithic.class);
    }
}
```

- [ ] **步骤 4：运行测试并验证应用程序根缺失**

运行：

```bash
./mvnw -q test
```

预期：编译失败，因为 `StocketApplication` 不存在。

- [ ] **步骤 5：添加应用程序根**

创建 `backend/src/main/java/com/familyassets/StocketApplication.java`：

```java
package com.familyassets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class StocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(StocketApplication.class, args);
    }
}
```

- [ ] **步骤 6：运行应用程序根测试**

运行：

```bash
./mvnw -q test
```

预期：通过，包含一个测试和 Java 21；测试验证两个必需的应用程序注解而不启动基础设施。

- [ ] **步骤 7：提交后端引导**

```bash
git add backend
git commit -m "build: bootstrap Java 21 Spring Boot backend"
```

### 任务二：强制执行模块化单体边界

**文件：**
- 创建：`backend/src/main/java/com/familyassets/identity/package-info.java`
- 创建：`backend/src/main/java/com/familyassets/catalog/package-info.java`
- 创建：`backend/src/main/java/com/familyassets/location/package-info.java`
- 创建：`backend/src/main/java/com/familyassets/inventory/package-info.java`
- 创建：`backend/src/main/java/com/familyassets/reminder/package-info.java`
- 创建：`backend/src/main/java/com/familyassets/notification/package-info.java`
- 创建：`backend/src/main/java/com/familyassets/attachment/package-info.java`
- 创建：`backend/src/main/java/com/familyassets/audit/package-info.java`
- 创建：`backend/src/test/java/com/familyassets/ArchitectureTest.java`

- [ ] **步骤 1：编写失败的架构测试**

创建 `backend/src/test/java/com/familyassets/ArchitectureTest.java`：

```java
package com.familyassets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.aot.DisabledInAotMode;

@DisabledInAotMode
class ArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(StocketApplication.class);

    @Test
    void moduleDependenciesAreValid() {
        modules.verify();
    }

    @Test
    void approvedModulesArePresent() {
        Set<String> names = modules.stream()
            .map(module -> module.getIdentifier().toString())
            .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder(
            "attachment", "audit", "catalog", "identity",
            "inventory", "location", "notification", "reminder", "system");
    }
}
```

- [ ] **步骤 2：运行架构测试**

运行：

```bash
./mvnw -q -Dtest=ArchitectureTest test
```

预期：失败，因为没有应用程序模块包存在。

- [ ] **步骤 3：声明八个业务模块**

创建以下八个文件，内容如下：

```java
// backend/src/main/java/com/familyassets/identity/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.familyassets.identity;

// backend/src/main/java/com/familyassets/catalog/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Catalog")
package com.familyassets.catalog;

// backend/src/main/java/com/familyassets/location/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Location")
package com.familyassets.location;

// backend/src/main/java/com/familyassets/inventory/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Inventory")
package com.familyassets.inventory;

// backend/src/main/java/com/familyassets/reminder/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Reminder")
package com.familyassets.reminder;

// backend/src/main/java/com/familyassets/notification/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package com.familyassets.notification;

// backend/src/main/java/com/familyassets/attachment/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Attachment")
package com.familyassets.attachment;

// backend/src/main/java/com/familyassets/audit/package-info.java
@org.springframework.modulith.ApplicationModule(displayName = "Audit")
package com.familyassets.audit;
```

- [ ] **步骤 4：重新运行模块验证**

运行：

```bash
./mvnw -q -Dtest=ArchitectureTest test
```

预期：仅因 `system` 基础模块尚未创建而失败；已声明的八个模块被发现且无循环依赖。

- [ ] **步骤 5：提交模块边界骨架**

不要在测试失败时提交。直接继续任务三，它创建 `system`；任务二和任务三共享一个绿色提交，因为 `system` 是已批准模块集的一部分。

### 任务三：添加系统 API 和通用错误契约

**文件：**
- 创建：`backend/src/main/java/com/familyassets/system/package-info.java`
- 创建：`backend/src/main/java/com/familyassets/system/SystemController.java`
- 创建：`backend/src/main/java/com/familyassets/system/SystemStatus.java`
- 创建：`backend/src/main/java/com/familyassets/system/ApiExceptionHandler.java`
- 创建：`backend/src/main/resources/application.yml`
- 测试：`backend/src/test/java/com/familyassets/system/SystemApiTest.java`

- [ ] **步骤 1：编写失败的系统 API 测试**

创建 `backend/src/test/java/com/familyassets/system/SystemApiTest.java`：

```java
package com.familyassets.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SystemController.class)
@org.springframework.context.annotation.Import({
    SystemApiTest.BuildInfoConfiguration.class,
    SystemApiTest.ValidationProbeController.class,
    ApiExceptionHandler.class
})
class SystemApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void returnsStableBuildInformation() throws Exception {
        mvc.perform(get("/api/v1/system"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("stocket"))
            .andExpect(jsonPath("$.version").isString());
    }

    @Test
    void returnsProblemDetailsForValidationErrors() throws Exception {
        mvc.perform(post("/api/v1/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.retryable").value(false))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("value"));
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class ValidationProbeController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody ValidationProbe request) {
        }
    }

    record ValidationProbe(@NotBlank String value) {
    }
}
```

- [ ] **步骤 2：验证系统 API 测试失败**

运行：

```bash
./mvnw -q -Dtest=SystemApiTest test
```

预期：失败，因为 `SystemController` 不存在。

- [ ] **步骤 3：声明并实现系统模块**

创建 `backend/src/main/java/com/familyassets/system/package-info.java`：

```java
@org.springframework.modulith.ApplicationModule(displayName = "System")
package com.familyassets.system;
```

创建 `backend/src/main/java/com/familyassets/system/SystemStatus.java`：

```java
package com.familyassets.system;

public record SystemStatus(String name, String version) {
}
```

创建 `backend/src/main/java/com/familyassets/system/SystemController.java`：

```java
package com.familyassets.system;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    private final String version;

    SystemController(BuildProperties buildProperties) {
        this.version = buildProperties.getVersion();
    }

    @GetMapping
    SystemStatus status() {
        return new SystemStatus("stocket", version);
    }
}
```

创建 `backend/src/main/java/com/familyassets/system/ApiExceptionHandler.java`：

```java
package com.familyassets.system;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(422);
        problem.setTitle("Validation failed");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("retryable", false);
        problem.setProperty("fieldErrors", exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
            .toList());
        return problem;
    }

    private record FieldError(String field, String message) {
    }
}
```

- [ ] **步骤 4：添加通用应用程序配置**

创建 `backend/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: stocket
  threads:
    virtual:
      enabled: true
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  mvc:
    problemdetails:
      enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,db
```

- [ ] **步骤 5：在测试中使构建属性可用**

在 `SystemApiTest` 内、闭合大括号之前添加此测试配置：

```java
    @org.springframework.boot.test.context.TestConfiguration
    static class BuildInfoConfiguration {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.info.BuildProperties buildProperties() {
            java.util.Properties properties = new java.util.Properties();
            properties.setProperty("version", "test");
            return new org.springframework.boot.info.BuildProperties(properties);
        }
    }
```

`SystemApiTest` 上已有的 `@Import` 会加载此构建配置、验证探测器和生产异常处理器。

- [ ] **步骤 6：运行系统和架构测试**

运行：

```bash
./mvnw -q -Dtest=SystemApiTest,ArchitectureTest test
```

预期：通过；所有九个模块均存在，`/api/v1/system` 返回构建信息。

- [ ] **步骤 7：提交模块边界和系统 API**

```bash
git add backend/src backend/pom.xml
git commit -m "feat: establish modular system API foundation"
```

### 任务四：建立 PostgreSQL 和 Flyway 基线

**文件：**
- 创建：`backend/src/main/resources/application-local.yml`
- 创建：`backend/src/main/resources/db/migration/V1__baseline.sql`
- 创建：`backend/src/test/java/com/familyassets/DatabaseMigrationTest.java`

- [ ] **步骤 1：编写失败的数据库迁移测试**

创建 `backend/src/test/java/com/familyassets/DatabaseMigrationTest.java`：

```java
package com.familyassets;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
class DatabaseMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    DataSource dataSource;

    @Test
    void installsRequiredExtensionsAndSchemaMarker() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
            "select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from app_schema_marker where version = 1", Integer.class))
            .isEqualTo(1);
    }
}
```

- [ ] **步骤 2：运行数据库测试**

在 Docker 可用的情况下运行：

```bash
./mvnw -q -Dtest=DatabaseMigrationTest test
```

预期：失败，因为 `V1__baseline.sql` 不存在。如果 Docker 不可用，记录该环境阻塞项，并在标记任务完成前在 CI 中运行此测试。

- [ ] **步骤 3：添加基线迁移**

创建 `backend/src/main/resources/db/migration/V1__baseline.sql`：

```sql
create extension if not exists pg_trgm;

create table app_schema_marker (
    version integer primary key,
    installed_at timestamptz not null default now()
);

insert into app_schema_marker (version) values (1);
```

- [ ] **步骤 4：添加本地数据库默认配置**

创建 `backend/src/main/resources/application-local.yml`：

```yaml
spring:
  datasource:
    url: ${STOCKET_DB_URL:jdbc:postgresql://localhost:5432/stocket}
    username: ${STOCKET_DB_USER:stocket}
    password: ${STOCKET_DB_PASSWORD:stocket}
```

- [ ] **步骤 5：运行迁移和完整后端测试**

运行：

```bash
./mvnw test
```

预期：通过；Testcontainers 应用 Flyway 迁移版本 1，Hibernate 验证空的业务模式而不生成 DDL。

- [ ] **步骤 6：提交数据库基线**

```bash
git add backend/src/main/resources backend/src/test/java/com/familyassets/DatabaseMigrationTest.java
git commit -m "feat: add PostgreSQL migration baseline"
```

### 任务五：引导 Vue 和 Element Plus 外壳

**文件：**
- 创建：`frontend/package.json`
- 创建：`frontend/package-lock.json`
- 创建：`frontend/index.html`
- 创建：`frontend/tsconfig.json`
- 创建：`frontend/tsconfig.app.json`
- 创建：`frontend/vite.config.ts`
- 创建：`frontend/src/env.d.ts`
- 创建：`frontend/src/main.ts`
- 创建：`frontend/src/App.vue`
- 创建：`frontend/src/styles/main.css`
- 创建：`frontend/src/test/setup.ts`
- 测试：`frontend/src/test/App.spec.ts`

- [ ] **步骤 1：添加固定的前端清单**

创建 `frontend/package.json`：

```json
{
  "name": "stocket-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "test": "vitest run",
    "typecheck": "vue-tsc -b"
  },
  "dependencies": {
    "@element-plus/icons-vue": "2.3.2",
    "element-plus": "2.11.0",
    "vue": "3.5.20"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "6.9.1",
    "@testing-library/vue": "8.1.0",
    "@types/node": "25.3.0",
    "@vitejs/plugin-vue": "7.0.0",
    "@vue/test-utils": "2.4.6",
    "@vue/tsconfig": "0.8.1",
    "jsdom": "27.0.0",
    "typescript": "5.9.3",
    "vite": "8.0.16",
    "vitest": "4.0.18",
    "vue-tsc": "3.0.6"
  }
}
```

运行：

```bash
npm install
```

预期：`package-lock.json` 被生成，`npm audit` 无未解决的严重漏洞。提交锁定文件；永远不要用范围版本替换精确版本。

- [ ] **步骤 2：编写失败的外壳测试**

创建 `frontend/src/test/setup.ts`：

```typescript
import '@testing-library/jest-dom/vitest'
import ElementPlus from 'element-plus'
import { config } from '@vue/test-utils'

config.global.plugins = [ElementPlus]
```

创建 `frontend/src/test/App.spec.ts`：

```typescript
import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import App from '../App.vue'

describe('App', () => {
  it('renders the mobile-first foundation shell', () => {
    render(App)
    expect(screen.getByRole('heading', { name: '家庭资产' })).toBeInTheDocument()
    expect(screen.getByText('工程基础已就绪')).toBeInTheDocument()
  })
})
```

- [ ] **步骤 3：配置 TypeScript 和 Vitest**

创建 `frontend/tsconfig.json`：

```json
{
  "files": [],
  "references": [{ "path": "./tsconfig.app.json" }]
}
```

创建 `frontend/tsconfig.app.json`：

```json
{
  "extends": "@vue/tsconfig/tsconfig.dom.json",
  "include": ["src/**/*.ts", "src/**/*.vue"],
  "compilerOptions": {
    "composite": true,
    "strict": true,
    "types": ["vitest/globals", "element-plus/global"]
  }
}
```

创建 `frontend/vite.config.ts`：

```typescript
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
```

创建 `frontend/src/env.d.ts`：

```typescript
/// <reference types="vite/client" />
```

- [ ] **步骤 4：运行测试并验证其失败**

运行：

```bash
npm test
```

预期：失败，因为 `src/App.vue` 不存在。

- [ ] **步骤 5：实现前端外壳**

创建 `frontend/index.html`：

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="theme-color" content="#2563eb" />
    <title>家庭资产</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

创建 `frontend/src/main.ts`：

```typescript
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/main.css'
import App from './App.vue'

createApp(App).use(ElementPlus).mount('#app')
```

创建 `frontend/src/App.vue`：

```vue
<script setup lang="ts">
import { Box, CircleCheck } from '@element-plus/icons-vue'
</script>

<template>
  <main class="app-shell">
    <section class="foundation-card">
      <el-icon :size="48" color="#2563eb"><Box /></el-icon>
      <h1>家庭资产</h1>
      <p>工程基础已就绪</p>
      <el-tag type="success" effect="light">
        <el-icon><CircleCheck /></el-icon>
        Java 21 · Spring Boot Native · Vue 3
      </el-tag>
    </section>
  </main>
</template>
```

创建 `frontend/src/styles/main.css`：

```css
:root {
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
  color: #0f172a;
  background: #f8fafc;
}

* { box-sizing: border-box; }
body { margin: 0; min-width: 320px; min-height: 100vh; }

.app-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.foundation-card {
  width: min(100%, 420px);
  padding: 40px 24px;
  text-align: center;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  box-shadow: 0 16px 40px rgb(15 23 42 / 8%);
}

.foundation-card h1 { margin: 16px 0 8px; }
.foundation-card p { margin: 0 0 20px; color: #64748b; }
```

- [ ] **步骤 6：运行前端测试和生产构建**

运行：

```bash
npm test
npm run typecheck
npm run build
```

预期：所有命令通过，`frontend/dist/index.html` 存在。

- [ ] **步骤 7：提交前端外壳**

```bash
git add frontend
git commit -m "feat: add Vue Element Plus application shell"
```

### 任务六：将前端外壳连接到系统 API

**文件：**
- 创建：`frontend/src/api/system.ts`
- 修改：`frontend/src/App.vue`
- 修改：`frontend/src/test/App.spec.ts`

- [ ] **步骤 1：用 API 状态测试替换外壳测试**

用以下内容替换 `frontend/src/test/App.spec.ts`：

```typescript
import { render, screen } from '@testing-library/vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App.vue'

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ name: 'stocket', version: '0.1.0-test' }),
    }))
  })

  it('shows the backend version when the system API is healthy', async () => {
    render(App)
    expect(await screen.findByText('后端 0.1.0-test 已连接')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/api/v1/system', { credentials: 'same-origin' })
  })
})
```

- [ ] **步骤 2：运行测试并验证其失败**

运行：

```bash
npm test
```

预期：失败，因为当前外壳从未调用 `/api/v1/system`。

- [ ] **步骤 3：添加类型化系统客户端**

创建 `frontend/src/api/system.ts`：

```typescript
export interface SystemStatus {
  name: string
  version: string
}

export async function getSystemStatus(): Promise<SystemStatus> {
  const response = await fetch('/api/v1/system', { credentials: 'same-origin' })
  if (!response.ok) throw new Error(`System API returned ${response.status}`)
  return response.json() as Promise<SystemStatus>
}
```

- [ ] **步骤 4：更新外壳以显示连接状态**

用以下内容替换 `frontend/src/App.vue` 中的脚本块：

```vue
<script setup lang="ts">
import { Box, CircleCheck, Warning } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { getSystemStatus } from './api/system'

const message = ref('正在连接后端…')
const connected = ref(false)

onMounted(async () => {
  try {
    const status = await getSystemStatus()
    connected.value = true
    message.value = `后端 ${status.version} 已连接`
  } catch {
    message.value = '后端暂不可用'
  }
})
</script>
```

用以下内容替换模板中现有的 `el-tag`：

```vue
<el-tag :type="connected ? 'success' : 'warning'" effect="light">
  <el-icon><CircleCheck v-if="connected" /><Warning v-else /></el-icon>
  {{ message }}
</el-tag>
```

- [ ] **步骤 5：运行前端验证**

运行：

```bash
npm test
npm run typecheck
npm run build
```

预期：通过；测试观察到完全相同的同源 API 请求和渲染的后端版本。

- [ ] **步骤 6：提交 API 集成**

```bash
git add frontend/src
git commit -m "feat: display backend health in frontend shell"
```

### 任务七：添加原生 Docker Compose 部署

**文件：**
- 创建：`.env.example`
- 创建：`deploy/compose.yml`
- 创建：`deploy/app/Dockerfile`
- 创建：`deploy/frontend/Dockerfile`
- 创建：`deploy/gateway/default.conf`

- [ ] **步骤 1：添加部署环境契约**

创建 `.env.example`：

```dotenv
POSTGRES_DB=stocket
POSTGRES_USER=stocket
POSTGRES_PASSWORD=stocket-local-dev
STOCKET_PORT=8088
STOCKET_DB_PASSWORD=stocket-local-dev
```

- [ ] **步骤 2：添加原生后端镜像**

创建 `deploy/app/Dockerfile`：

```dockerfile
FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /workspace
COPY backend/.mvn backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src ./src
RUN ./mvnw -B -Pnative -DskipTests native:compile

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /workspace/target/stocket-backend ./stocket
USER 10001
EXPOSE 8080
ENTRYPOINT ["/app/stocket"]
```

- [ ] **步骤 3：添加前端镜像和网关配置**

创建 `deploy/frontend/Dockerfile`：

```dockerfile
FROM node:24-alpine AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.29-alpine
COPY deploy/gateway/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
```

创建 `deploy/gateway/default.conf`：

```nginx
server {
    listen 8080;
    server_name _;
    root /usr/share/nginx/html;

    location /api/ {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Request-Id $request_id;
    }

    location /actuator/health/ {
        proxy_pass http://app:8080;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **步骤 4：添加 Docker Compose**

创建 `deploy/compose.yml`：

```yaml
services:
  postgres:
    image: postgres:17.5-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20

  app:
    build:
      context: ..
      dockerfile: deploy/app/Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: local
      STOCKET_DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      STOCKET_DB_USER: ${POSTGRES_USER}
      STOCKET_DB_PASSWORD: ${STOCKET_DB_PASSWORD}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 10s
      timeout: 3s
      retries: 12

  gateway:
    build:
      context: ..
      dockerfile: deploy/frontend/Dockerfile
    ports:
      - "${STOCKET_PORT:-8088}:8080"
    depends_on:
      app:
        condition: service_healthy

volumes:
  postgres-data:
```

- [ ] **步骤 5：在长时间原生构建前验证 Compose 语法**

运行：

```bash
cp .env.example .env
docker compose --env-file .env -f deploy/compose.yml config --quiet
```

预期：通过，无模式或插值错误。提交的密码仅用于本地回环开发；在任何共享或可从互联网访问的部署前替换 `.env` 中的密码。被忽略的 `.env` 文件不得提交。

- [ ] **步骤 6：构建并启动原生技术栈**

运行：

```bash
docker compose --env-file .env -f deploy/compose.yml up --build -d
curl --fail http://localhost:8088/api/v1/system
curl --fail http://localhost:8088/actuator/health/readiness
```

预期：两个 `curl` 命令均返回 HTTP 200；系统 JSON 包含 `stocket`，就绪状态报告 `UP`。

- [ ] **步骤 7：停止技术栈而不删除数据**

运行：

```bash
docker compose --env-file .env -f deploy/compose.yml down
```

预期：容器停止；命名的 PostgreSQL 卷保留。

- [ ] **步骤 8：提交部署资源**

```bash
git add .env.example deploy
git commit -m "build: add native Docker Compose deployment"
```

### 任务八：添加稳定的开发者命令和 CI

**文件：**
- 创建：`Makefile`
- 创建：`.github/workflows/ci.yml`
- 修改：`README.md`

- [ ] **步骤 1：添加稳定的本地命令别名**

创建 `Makefile`：

```makefile
.PHONY: test backend-test frontend-test build aot native-test compose-config

test: backend-test frontend-test

backend-test:
	cd backend && ./mvnw test

frontend-test:
	cd frontend && npm test && npm run typecheck

build:
	cd backend && ./mvnw package
	cd frontend && npm run build

aot:
	cd backend && ./mvnw -Pnative spring-boot:process-aot

native-test:
	cd backend && ./mvnw -PnativeTest test

compose-config:
	docker compose --env-file .env.example -f deploy/compose.yml config --quiet
```

- [ ] **步骤 2：添加 JVM、前端、PostgreSQL、AOT 和原生测试的 CI**

创建 `.github/workflows/ci.yml`：

```yaml
name: ci

on:
  push:
    branches: [main]
  pull_request:

jobs:
  jvm-and-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - uses: actions/setup-node@v4
        with:
          node-version: '24'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: cd backend && ./mvnw test
      - run: cd frontend && npm ci
      - run: cd frontend && npm test
      - run: cd frontend && npm run typecheck
      - run: cd frontend && npm run build
      - run: cd backend && ./mvnw -Pnative spring-boot:process-aot
      - run: docker compose --env-file .env.example -f deploy/compose.yml config --quiet

  native-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: graalvm-community
          github-token: ${{ secrets.GITHUB_TOKEN }}
          cache: maven
      - run: cd backend && ./mvnw -PnativeTest test
```

- [ ] **步骤 3：用经验证的开发者说明替换 README**

用以下内容替换 `README.md`：

```markdown
# 家庭资产

面向单个家庭、多位成员的自托管家庭资产管理系统。

## 环境要求

- JDK 21
- Node.js 24 和 npm
- Docker with Compose
- 仅在 Docker 外编译原生可执行文件时需要 GraalVM 21

## 验证

```bash
make test
make build
make aot
make compose-config
```

使用 GraalVM 21 运行原生测试套件：

```bash
make native-test
```

## 本地后端

启动 PostgreSQL，然后运行：

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## 本地前端

```bash
cd frontend
npm install
npm run dev
```

Vite 将 `/api` 代理到 `http://localhost:8080`。

## 原生 Docker 技术栈

```bash
cp .env.example .env
# 在共享部署前替换本地开发密码。
docker compose --env-file .env -f deploy/compose.yml up --build -d
```

打开 `http://localhost:8088`。

## 文档

- 设计文档：`docs/superpowers/specs/2026-07-10-stocket-design.md`
- 交付路线图：`docs/superpowers/plans/2026-07-11-delivery-roadmap.md`
```

- [ ] **步骤 4：运行所有快速验证命令**

运行：

```bash
make test
make build
make aot
make compose-config
```

预期：所有目标通过。`make aot` 创建生成的 AOT 源码，无不支持的反射或代理错误。

- [ ] **步骤 5：运行原生验证门禁**

在 GraalVM 21 机器上运行：

```bash
make native-test
```

预期：通过。如果本地 GraalVM 不可用，GitHub Actions `native-test` 作业必须在此任务完成前通过。

- [ ] **步骤 6：提交 CI 和开发者文档**

```bash
git add Makefile .github/workflows/ci.yml README.md
git commit -m "ci: verify JVM frontend AOT and native builds"
```

### 任务九：最终基础验收

**文件：**
- 仅在验证暴露缺陷时修改；不要添加后续阶段的功能。

- [ ] **步骤 1：验收前验证仓库是否干净**

运行：

```bash
git status --short
```

预期：无输出。

- [ ] **步骤 2：从干净检出运行完整的快速测试矩阵**

运行：

```bash
make test
make build
make aot
make compose-config
```

预期：所有命令退出码为 0。

- [ ] **步骤 3：运行原生部署冒烟测试**

运行：

```bash
docker compose --env-file .env -f deploy/compose.yml up --build -d
curl --fail --retry 20 --retry-delay 3 http://localhost:8088/api/v1/system
curl --fail http://localhost:8088/actuator/health/readiness
docker compose --env-file .env -f deploy/compose.yml down
```

预期：原生后端和前端网关返回 HTTP 200，PostgreSQL 迁移版本 1 已安装，关机保留数据库卷。

- [ ] **步骤 4：检查提交历史和仓库状态**

运行：

```bash
git log --oneline --decorate -10
git status --short --branch
```

预期：针对后端引导、模块/系统基础、数据库基线、前端外壳、API 集成、部署和 CI 有聚焦的提交；分支干净。

- [ ] **步骤 5：记录阶段完成**

仅在本地或 CI 原生验证通过后，向 `README.md` 追加 `阶段一完成` 部分：

```markdown
## 阶段一完成

JVM 测试套件、前端测试/构建、Spring AOT 处理、PostgreSQL 迁移测试、GraalVM 原生测试和原生 Docker 冒烟测试均已为工程基础通过。
```

然后提交：

```bash
git add README.md
git commit -m "docs: record engineering foundation acceptance"
```

## 阶段一完成标准

- Java 编译使用 release 21。
- Spring Boot 上下文、模块验证、PostgreSQL 迁移和系统 API 测试通过。
- 模块模型恰好包含已批准的九个包且无循环。
- Vue 测试、类型检查和生产构建通过，且有已提交的锁定文件。
- Spring AOT 处理和 `nativeTest` Maven 配置通过。
- Docker Compose 原生技术栈返回健康的系统和就绪端点。
- 身份、目录、位置、库存、提醒、通知、附件或审计业务行为均未泄漏到此基础阶段。
- Git 工作树干净，每个任务有聚焦的提交。
