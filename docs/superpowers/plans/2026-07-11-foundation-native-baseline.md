# Family Assets Engineering Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a production-shaped Family Assets repository that runs a Java 21/Spring Boot 4 modular monolith and Vue 3 frontend, connects to PostgreSQL, starts through Docker Compose, and passes JVM, AOT, and GraalVM native smoke tests.

**Architecture:** The backend is one Spring Boot application split into eight Spring Modulith packages. Phase 1 contains infrastructure and explicit module shells only; business entities remain for later plans. The frontend is an independently built Vue application served by Nginx in deployment, with `/api` reverse-proxied to the native backend.

**Tech Stack:** Java 21 LTS, Spring Boot 4.0.3, Spring Modulith 2.0.5, Maven 3.9.x, Flyway, PostgreSQL 17, Testcontainers, GraalVM Native Build Tools, Vue 3.5.20, TypeScript 5.9.3, Vite 8.0.16, Element Plus 2.11.0, Vitest, Docker Compose

---

## Scope Guard

This plan establishes only the executable foundation. It deliberately does not implement users, sessions, categories, locations, item records, inventory, notifications, attachments, PWA offline behavior, or business APIs. Those belong to later roadmap phases.

## Target File Map

```text
family-assets/
├── .github/workflows/ci.yml                    # JVM, frontend, AOT, native-test CI
├── .env.example                                # Non-secret deployment contract
├── Makefile                                    # Stable developer entry points
├── README.md                                   # Setup and verification commands
├── backend/
│   ├── .mvn/wrapper/maven-wrapper.properties   # Maven wrapper version
│   ├── mvnw, mvnw.cmd                          # Reproducible Maven entry points
│   ├── pom.xml                                 # Java, Boot, Modulith, native dependencies
│   └── src/
│       ├── main/java/com/familyassets/
│       │   ├── FamilyAssetsApplication.java    # Application and @Modulithic root
│       │   ├── system/                         # Foundation-only system API
│       │   ├── identity/package-info.java      # Module declaration
│       │   ├── catalog/package-info.java
│       │   ├── location/package-info.java
│       │   ├── inventory/package-info.java
│       │   ├── reminder/package-info.java
│       │   ├── notification/package-info.java
│       │   ├── attachment/package-info.java
│       │   └── audit/package-info.java
│       ├── main/resources/
│       │   ├── application.yml                 # Common typed configuration
│       │   ├── application-local.yml           # Local developer defaults
│       │   └── db/migration/V1__baseline.sql   # Extensions and schema marker
│       └── test/java/com/familyassets/
│           ├── FamilyAssetsApplicationTests.java
│           ├── ArchitectureTest.java
│           ├── DatabaseMigrationTest.java
│           └── system/SystemApiTest.java
├── frontend/
│   ├── package.json, package-lock.json          # Pinned JS dependency graph
│   ├── index.html, tsconfig*.json, vite.config.ts
│   └── src/
│       ├── App.vue                             # Responsive shell
│       ├── main.ts                             # Vue/Element bootstrap
│       ├── api/system.ts                       # Typed system API client
│       ├── styles/main.css                     # Mobile-first global styles
│       └── test/App.spec.ts                    # Shell/API status test
└── deploy/
    ├── compose.yml                             # gateway, native app, PostgreSQL
    ├── app/Dockerfile                          # Native build and runtime image
    ├── frontend/Dockerfile                     # Vite build and Nginx image
    └── gateway/default.conf                    # SPA fallback and API proxy
```

### Task 1: Bootstrap the Java 21 Maven Application

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `backend/mvnw`
- Create: `backend/mvnw.cmd`
- Create: `backend/src/main/java/com/familyassets/FamilyAssetsApplication.java`
- Create: `backend/src/test/java/com/familyassets/FamilyAssetsApplicationTests.java`

- [ ] **Step 1: Add the Maven build descriptor**

Create `backend/pom.xml`:

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
  <artifactId>family-assets-backend</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>family-assets-backend</name>
  <description>Family Assets modular monolith backend</description>

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

- [ ] **Step 2: Generate the Maven wrapper**

Run from `backend/`:

```bash
mvn -N wrapper:wrapper -Dmaven=3.9.12
```

Expected: `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties` exist; the properties file points to Maven 3.9.12.

- [ ] **Step 3: Write the failing application-root test**

Create `backend/src/test/java/com/familyassets/FamilyAssetsApplicationTests.java`:

```java
package com.familyassets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

class FamilyAssetsApplicationTests {

    @Test
    void applicationRootDeclaresBootAndModulith() {
        assertThat(FamilyAssetsApplication.class)
            .hasAnnotation(SpringBootApplication.class)
            .hasAnnotation(Modulithic.class);
    }
}
```

- [ ] **Step 4: Run the test and verify the application root is missing**

Run:

```bash
./mvnw -q test
```

Expected: FAIL to compile because `FamilyAssetsApplication` does not exist.

- [ ] **Step 5: Add the application root**

Create `backend/src/main/java/com/familyassets/FamilyAssetsApplication.java`:

```java
package com.familyassets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class FamilyAssetsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyAssetsApplication.class, args);
    }
}
```

- [ ] **Step 6: Run the application-root test**

Run:

```bash
./mvnw -q test
```

Expected: PASS with one test and Java 21; the test verifies the two required application annotations without starting infrastructure.

- [ ] **Step 7: Commit the backend bootstrap**

```bash
git add backend
git commit -m "build: bootstrap Java 21 Spring Boot backend"
```

### Task 2: Enforce Modular Monolith Boundaries

**Files:**
- Create: `backend/src/main/java/com/familyassets/identity/package-info.java`
- Create: `backend/src/main/java/com/familyassets/catalog/package-info.java`
- Create: `backend/src/main/java/com/familyassets/location/package-info.java`
- Create: `backend/src/main/java/com/familyassets/inventory/package-info.java`
- Create: `backend/src/main/java/com/familyassets/reminder/package-info.java`
- Create: `backend/src/main/java/com/familyassets/notification/package-info.java`
- Create: `backend/src/main/java/com/familyassets/attachment/package-info.java`
- Create: `backend/src/main/java/com/familyassets/audit/package-info.java`
- Create: `backend/src/test/java/com/familyassets/ArchitectureTest.java`

- [ ] **Step 1: Write the failing architecture test**

Create `backend/src/test/java/com/familyassets/ArchitectureTest.java`:

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

    private final ApplicationModules modules = ApplicationModules.of(FamilyAssetsApplication.class);

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

- [ ] **Step 2: Run the architecture test**

Run:

```bash
./mvnw -q -Dtest=ArchitectureTest test
```

Expected: FAIL because no application module packages exist.

- [ ] **Step 3: Declare the eight business modules**

Create the eight files with these exact contents:

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

- [ ] **Step 4: Re-run module verification**

Run:

```bash
./mvnw -q -Dtest=ArchitectureTest test
```

Expected: FAIL only because the `system` foundation module has not been created; the eight declared modules are discovered and have no cycles.

- [ ] **Step 5: Commit the module boundary skeleton**

Do not commit while the test is red. Continue directly to Task 3, which creates `system`; Tasks 2 and 3 share one green commit because `system` is intentionally part of the approved module set.

### Task 3: Add the System API and Common Error Contract

**Files:**
- Create: `backend/src/main/java/com/familyassets/system/package-info.java`
- Create: `backend/src/main/java/com/familyassets/system/SystemController.java`
- Create: `backend/src/main/java/com/familyassets/system/SystemStatus.java`
- Create: `backend/src/main/java/com/familyassets/system/ApiExceptionHandler.java`
- Create: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/familyassets/system/SystemApiTest.java`

- [ ] **Step 1: Write the failing system API test**

Create `backend/src/test/java/com/familyassets/system/SystemApiTest.java`:

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
            .andExpect(jsonPath("$.name").value("family-assets"))
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

- [ ] **Step 2: Verify the system API test fails**

Run:

```bash
./mvnw -q -Dtest=SystemApiTest test
```

Expected: FAIL because `SystemController` does not exist.

- [ ] **Step 3: Declare and implement the system module**

Create `backend/src/main/java/com/familyassets/system/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "System")
package com.familyassets.system;
```

Create `backend/src/main/java/com/familyassets/system/SystemStatus.java`:

```java
package com.familyassets.system;

public record SystemStatus(String name, String version) {
}
```

Create `backend/src/main/java/com/familyassets/system/SystemController.java`:

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
        return new SystemStatus("family-assets", version);
    }
}
```

Create `backend/src/main/java/com/familyassets/system/ApiExceptionHandler.java`:

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

- [ ] **Step 4: Add common application configuration**

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: family-assets
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

- [ ] **Step 5: Make build properties available in tests**

Add this test configuration inside `SystemApiTest`, immediately before its closing brace:

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

The `@Import` already present on `SystemApiTest` loads this build configuration, the validation probe, and the production exception handler.

- [ ] **Step 6: Run system and architecture tests**

Run:

```bash
./mvnw -q -Dtest=SystemApiTest,ArchitectureTest test
```

Expected: PASS; all nine modules are present and `/api/v1/system` returns build information.

- [ ] **Step 7: Commit module boundaries and system API**

```bash
git add backend/src backend/pom.xml
git commit -m "feat: establish modular system API foundation"
```

### Task 4: Establish PostgreSQL and Flyway Baseline

**Files:**
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/db/migration/V1__baseline.sql`
- Create: `backend/src/test/java/com/familyassets/DatabaseMigrationTest.java`

- [ ] **Step 1: Write the failing database migration test**

Create `backend/src/test/java/com/familyassets/DatabaseMigrationTest.java`:

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

- [ ] **Step 2: Run the database test**

Run with Docker available:

```bash
./mvnw -q -Dtest=DatabaseMigrationTest test
```

Expected: FAIL because `V1__baseline.sql` does not exist. If Docker is unavailable, record that environment blocker and run this test in CI before marking the task complete.

- [ ] **Step 3: Add the baseline migration**

Create `backend/src/main/resources/db/migration/V1__baseline.sql`:

```sql
create extension if not exists pg_trgm;

create table app_schema_marker (
    version integer primary key,
    installed_at timestamptz not null default now()
);

insert into app_schema_marker (version) values (1);
```

- [ ] **Step 4: Add local database defaults**

Create `backend/src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: ${FAMILY_ASSETS_DB_URL:jdbc:postgresql://localhost:5432/family_assets}
    username: ${FAMILY_ASSETS_DB_USER:family_assets}
    password: ${FAMILY_ASSETS_DB_PASSWORD:family_assets}
```

- [ ] **Step 5: Run migration and full backend tests**

Run:

```bash
./mvnw test
```

Expected: PASS; Testcontainers applies Flyway migration version 1 and Hibernate validates the empty business schema without generating DDL.

- [ ] **Step 6: Commit the database baseline**

```bash
git add backend/src/main/resources backend/src/test/java/com/familyassets/DatabaseMigrationTest.java
git commit -m "feat: add PostgreSQL migration baseline"
```

### Task 5: Bootstrap the Vue and Element Plus Shell

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/index.html`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.app.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/src/env.d.ts`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/styles/main.css`
- Create: `frontend/src/test/setup.ts`
- Test: `frontend/src/test/App.spec.ts`

- [ ] **Step 1: Add the pinned frontend manifest**

Create `frontend/package.json`:

```json
{
  "name": "family-assets-frontend",
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

Run:

```bash
npm install
```

Expected: `package-lock.json` is generated and `npm audit` has no unresolved critical vulnerability. Commit the lock file; never replace exact versions with ranges.

- [ ] **Step 2: Write the failing shell test**

Create `frontend/src/test/setup.ts`:

```typescript
import '@testing-library/jest-dom/vitest'
import ElementPlus from 'element-plus'
import { config } from '@vue/test-utils'

config.global.plugins = [ElementPlus]
```

Create `frontend/src/test/App.spec.ts`:

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

- [ ] **Step 3: Configure TypeScript and Vitest**

Create `frontend/tsconfig.json`:

```json
{
  "files": [],
  "references": [{ "path": "./tsconfig.app.json" }]
}
```

Create `frontend/tsconfig.app.json`:

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

Create `frontend/vite.config.ts`:

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

Create `frontend/src/env.d.ts`:

```typescript
/// <reference types="vite/client" />
```

- [ ] **Step 4: Run the test and verify it fails**

Run:

```bash
npm test
```

Expected: FAIL because `src/App.vue` does not exist.

- [ ] **Step 5: Implement the frontend shell**

Create `frontend/index.html`:

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

Create `frontend/src/main.ts`:

```typescript
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/main.css'
import App from './App.vue'

createApp(App).use(ElementPlus).mount('#app')
```

Create `frontend/src/App.vue`:

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

Create `frontend/src/styles/main.css`:

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

- [ ] **Step 6: Run frontend tests and production build**

Run:

```bash
npm test
npm run typecheck
npm run build
```

Expected: all commands PASS and `frontend/dist/index.html` exists.

- [ ] **Step 7: Commit the frontend shell**

```bash
git add frontend
git commit -m "feat: add Vue Element Plus application shell"
```

### Task 6: Connect the Frontend Shell to the System API

**Files:**
- Create: `frontend/src/api/system.ts`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/test/App.spec.ts`

- [ ] **Step 1: Replace the shell test with an API status test**

Replace `frontend/src/test/App.spec.ts` with:

```typescript
import { render, screen } from '@testing-library/vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App.vue'

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ name: 'family-assets', version: '0.1.0-test' }),
    }))
  })

  it('shows the backend version when the system API is healthy', async () => {
    render(App)
    expect(await screen.findByText('后端 0.1.0-test 已连接')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/api/v1/system', { credentials: 'same-origin' })
  })
})
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
npm test
```

Expected: FAIL because the current shell never calls `/api/v1/system`.

- [ ] **Step 3: Add the typed system client**

Create `frontend/src/api/system.ts`:

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

- [ ] **Step 4: Update the shell to show connection state**

Replace the script block in `frontend/src/App.vue` with:

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

Replace the existing `el-tag` in the template with:

```vue
<el-tag :type="connected ? 'success' : 'warning'" effect="light">
  <el-icon><CircleCheck v-if="connected" /><Warning v-else /></el-icon>
  {{ message }}
</el-tag>
```

- [ ] **Step 5: Run frontend verification**

Run:

```bash
npm test
npm run typecheck
npm run build
```

Expected: PASS; the test observes the exact same-origin API request and rendered backend version.

- [ ] **Step 6: Commit API integration**

```bash
git add frontend/src
git commit -m "feat: display backend health in frontend shell"
```

### Task 7: Add Native Docker Compose Deployment

**Files:**
- Create: `.env.example`
- Create: `deploy/compose.yml`
- Create: `deploy/app/Dockerfile`
- Create: `deploy/frontend/Dockerfile`
- Create: `deploy/gateway/default.conf`

- [ ] **Step 1: Add the deployment environment contract**

Create `.env.example`:

```dotenv
POSTGRES_DB=family_assets
POSTGRES_USER=family_assets
POSTGRES_PASSWORD=family-assets-local-dev
FAMILY_ASSETS_PORT=8088
FAMILY_ASSETS_DB_PASSWORD=family-assets-local-dev
```

- [ ] **Step 2: Add the native backend image**

Create `deploy/app/Dockerfile`:

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
COPY --from=build /workspace/target/family-assets-backend ./family-assets
USER 10001
EXPOSE 8080
ENTRYPOINT ["/app/family-assets"]
```

- [ ] **Step 3: Add the frontend image and gateway configuration**

Create `deploy/frontend/Dockerfile`:

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

Create `deploy/gateway/default.conf`:

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

- [ ] **Step 4: Add Docker Compose**

Create `deploy/compose.yml`:

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
      FAMILY_ASSETS_DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      FAMILY_ASSETS_DB_USER: ${POSTGRES_USER}
      FAMILY_ASSETS_DB_PASSWORD: ${FAMILY_ASSETS_DB_PASSWORD}
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
      - "${FAMILY_ASSETS_PORT:-8088}:8080"
    depends_on:
      app:
        condition: service_healthy

volumes:
  postgres-data:
```

- [ ] **Step 5: Validate Compose syntax before a long native build**

Run:

```bash
cp .env.example .env
docker compose --env-file .env -f deploy/compose.yml config --quiet
```

Expected: PASS with no schema or interpolation errors. The committed password is for loopback development only; replace it in `.env` before any shared or Internet-accessible deployment. The ignored `.env` file must not be committed.

- [ ] **Step 6: Build and start the native stack**

Run:

```bash
docker compose --env-file .env -f deploy/compose.yml up --build -d
curl --fail http://localhost:8088/api/v1/system
curl --fail http://localhost:8088/actuator/health/readiness
```

Expected: both `curl` commands return HTTP 200; system JSON contains `family-assets`, and readiness reports `UP`.

- [ ] **Step 7: Stop the stack without deleting data**

Run:

```bash
docker compose --env-file .env -f deploy/compose.yml down
```

Expected: containers stop; the named PostgreSQL volume remains.

- [ ] **Step 8: Commit deployment resources**

```bash
git add .env.example deploy
git commit -m "build: add native Docker Compose deployment"
```

### Task 8: Add Stable Developer Commands and CI

**Files:**
- Create: `Makefile`
- Create: `.github/workflows/ci.yml`
- Modify: `README.md`

- [ ] **Step 1: Add stable local command aliases**

Create `Makefile`:

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

- [ ] **Step 2: Add CI for JVM, frontend, PostgreSQL, AOT, and native tests**

Create `.github/workflows/ci.yml`:

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

- [ ] **Step 3: Replace README with verified developer instructions**

Replace `README.md` with:

```markdown
# Family Assets

面向单个家庭、多位成员的自托管家庭资产管理系统。

## Requirements

- JDK 21
- Node.js 24 and npm
- Docker with Compose
- GraalVM 21 only when compiling native executables outside Docker

## Verify

```bash
make test
make build
make aot
make compose-config
```

Run the native test suite with GraalVM 21:

```bash
make native-test
```

## Local Backend

Start PostgreSQL, then run:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Local Frontend

```bash
cd frontend
npm install
npm run dev
```

Vite proxies `/api` to `http://localhost:8080`.

## Native Docker Stack

```bash
cp .env.example .env
# Replace the local-development passwords before shared deployment.
docker compose --env-file .env -f deploy/compose.yml up --build -d
```

Open `http://localhost:8088`.

## Documentation

- Design: `docs/superpowers/specs/2026-07-10-family-assets-design.md`
- Delivery roadmap: `docs/superpowers/plans/2026-07-11-delivery-roadmap.md`
```

- [ ] **Step 4: Run every fast verification command**

Run:

```bash
make test
make build
make aot
make compose-config
```

Expected: all targets PASS. `make aot` creates generated AOT sources without unsupported reflection or proxy errors.

- [ ] **Step 5: Run the native verification gate**

Run on a GraalVM 21 machine:

```bash
make native-test
```

Expected: PASS. If local GraalVM is unavailable, the GitHub Actions `native-test` job must pass before this task is complete.

- [ ] **Step 6: Commit CI and developer documentation**

```bash
git add Makefile .github/workflows/ci.yml README.md
git commit -m "ci: verify JVM frontend AOT and native builds"
```

### Task 9: Final Foundation Acceptance

**Files:**
- Modify only if verification exposes a defect; do not add later-phase features.

- [ ] **Step 1: Verify the repository is clean before acceptance**

Run:

```bash
git status --short
```

Expected: no output.

- [ ] **Step 2: Run the full fast test matrix from a clean checkout**

Run:

```bash
make test
make build
make aot
make compose-config
```

Expected: all commands exit 0.

- [ ] **Step 3: Run the native deployment smoke test**

Run:

```bash
docker compose --env-file .env -f deploy/compose.yml up --build -d
curl --fail --retry 20 --retry-delay 3 http://localhost:8088/api/v1/system
curl --fail http://localhost:8088/actuator/health/readiness
docker compose --env-file .env -f deploy/compose.yml down
```

Expected: the native backend and frontend gateway return HTTP 200, PostgreSQL migration version 1 is installed, and shutdown preserves the database volume.

- [ ] **Step 4: Inspect commit history and repository state**

Run:

```bash
git log --oneline --decorate -10
git status --short --branch
```

Expected: focused commits for backend bootstrap, module/system foundation, database baseline, frontend shell, API integration, deployment, and CI; branch is clean.

- [ ] **Step 5: Record phase completion**

Append a `Phase 1 Complete` section to `README.md` only after local or CI native verification passes:

```markdown
## Phase 1 Complete

The JVM test suite, frontend test/build, Spring AOT processing, PostgreSQL migration test, GraalVM native tests, and native Docker smoke test pass for the engineering foundation.
```

Then commit:

```bash
git add README.md
git commit -m "docs: record engineering foundation acceptance"
```

## Phase 1 Completion Criteria

- Java compilation uses release 21.
- Spring Boot context, module verification, PostgreSQL migration, and system API tests pass.
- The module model contains exactly the approved nine packages and no cycle.
- Vue tests, type checking, and production build pass with a committed lock file.
- Spring AOT processing and the `nativeTest` Maven profile pass.
- The Docker Compose native stack returns healthy system and readiness endpoints.
- No identity, catalog, location, inventory, reminder, notification, attachment, or audit business behavior has leaked into this foundation phase.
- The Git working tree is clean and each task has a focused commit.
