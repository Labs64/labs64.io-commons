# Integrating OpenAPI Authorization into a Labs64.IO Module

This guide explains how to add OpenAPI-driven authorization policies to a Java
backend using standard OpenAPI `security`, the `x-labs64.auth` extension, and
`OpenApiAuthPreprocessorCli`.

## How It Works

The pipeline has four stages:

```
OpenAPI spec (security + x-labs64.auth)
  → OpenApiAuthPreprocessorCli (build-time)
    → Cleaned OpenAPI (annotations injected)
    → Cerbos resource policies (YAML) + routing manifest
  → openapi-generator (generates Java interfaces with annotations)
  → auth-context-spring-boot-starter (runtime enforcement via @Authorize interceptors and central Cerbos PDP)
```

1. **Author**: Declare OAuth2 scopes with standard `security`; add
   `x-labs64.auth` only when an operation needs a tenant or domain resource.
2. **Build-time**: The preprocessor injects Java annotations
   (`@RequireTenant`, `@RequireScopes`, `@Authorize`, `@PublicEndpoint`) and
   emits Cerbos resource policies plus routing/public-path manifests.
3. **Code generation**: OpenAPI Generator produces Java API interfaces with the injected annotations
4. **Runtime**: `auth-context-spring-boot-starter` auto-configures filters and interceptors that enforce the annotations

## Step 1: Declare OpenAPI Security and Labs64 Metadata

Register the OAuth2 scheme once:

```yaml
components:
  securitySchemes:
    oauth:
      type: oauth2
      flows:
        clientCredentials:
          tokenUrl: http://mock-oidc.localhost/labs64io/token
          scopes:
            my-resource:read: Read resources.
            my-resource:write: Create and update resources.
```

You may set a global authenticated default:

```yaml
security:
  - oauth: []
```

### Require tenant + scopes (authenticated endpoint)

```yaml
paths:
  /my-resource:
    get:
      operationId: listMyResources
      security:
        - oauth:
            - my-resource:read
      x-labs64:
        auth:
          tenant: true
```

### Require tenant only (no scope check)

```yaml
paths:
  /my-resource:
    post:
      operationId: createMyResource
      security:
        - oauth: []
      x-labs64:
        auth:
          tenant: true
```

### Public endpoint (no auth required)

```yaml
paths:
  /health:
    get:
      operationId: healthCheck
      security: []
```

When there is no global security, an operation with no effective `security` and
no `x-labs64.auth` requirements is also inferred public. `security: []` is the
standard explicit override when a global security requirement exists.

### Path-level Labs64 metadata

```yaml
paths:
  /my-resource:
    x-labs64:
      auth:
        tenant: true
    get:
      operationId: listMyResources
      security:
        - oauth:
            - my-resource:read
      # Inherits tenant=true
    post:
      operationId: createMyResource
      security:
        - oauth:
            - my-resource:write
      # Also inherits tenant=true
```

### Domain (Tier-2) resource authorization

Declare `resource: <Type>` to have the operation authorized in-process by the
module's `@Authorize` PEP against a typed Cerbos resource. This is
the OpenAPI-native source for the generated domain policy set
(`auth-policy-domain.cerbos`): the preprocessor emits one `permit` keyed on the
operationId, conditioned on the same `tenant`/`scopes`, plus one structural
cross-tenant guard per resource type. Operations without `resource` are
edge-only (coarse reachability).

```yaml
paths:
  /payments/{paymentId}/pay:
    post:
      operationId: payPayment
      security:
        - oauth:
            - payment:pay
      x-labs64:
        auth:
          tenant: true
          resource: Payment      # → generated domain permit + tenant guard
```

The module supplies the resource attributes at request time via a
`ResourceResolver`.
Fine-grained resource-attribute rules (workflow status, ownership) are **not**
expressible from OpenAPI — they stay in the service layer (and later Postgres
RLS), by design.

### Public inference

An operation receives `@PublicEndpoint` only when:

- its effective OpenAPI security does not require the `oauth` scheme; and
- it has no generated `@RequireTenant`, `@RequireScopes`, or `@Authorize`.

`oauth: []` still requires authentication, even though it generates no scope
annotation. Other security schemes do not count as Labs64 OAuth requirements.
Operation-level `security` overrides root-level `security`.

## Step 2: Configure the Maven Build

Add three things to your `pom.xml`:

### 2a. Add properties

```xml
<properties>
    <!-- Existing properties... -->

    <!-- OpenAPI auth preprocessor -->
    <exec-maven-plugin.version>3.6.2</exec-maven-plugin.version>
    <openapi.source>${project.basedir}/../your-api-module/src/main/resources/openapi/openapi-your-module.yaml</openapi.source>
    <openapi.generated>${project.build.directory}/generated-openapi/openapi-your-module.yaml</openapi.generated>
    <!-- Cerbos policies + routes manifest generated from OpenAPI security
         and x-labs64.auth.
         auth-policy.module must equal the module's gateway prefix
         (= policy-sources.yaml name); it is baked into the Cerbos resource
         kind (your-module -> your_module_api). Generated output is a build
         artifact only — the central Cerbos PDP owns the live policy set. -->
    <auth-policy-cerbos.output>${project.build.directory}/cerbos</auth-policy-cerbos.output>
    <auth-policy.module>your-module</auth-policy.module>
</properties>
```

### 2b. Add `jackson-dataformat-yaml` dependency

The preprocessor needs YAML parsing at build time:

```xml
<dependencies>
    <!-- Existing dependencies... -->

    <!-- YAML support for OpenApiAuthPreprocessor (build-time only) -->
    <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-yaml</artifactId>
        <version>${jackson-datatype.version}</version>
    </dependency>
</dependencies>
```

### 2c. Add `exec-maven-plugin` and update `openapi-generator-maven-plugin`

The preprocessor must run **before** the OpenAPI generator. Add `exec-maven-plugin` and point the generator at the generated (cleaned) OpenAPI:

```xml
<build>
    <plugins>
        <!-- 1. Auth preprocessor: runs first -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <version>${exec-maven-plugin.version}</version>
            <executions>
                <execution>
                    <id>generate-auth-openapi-policy</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>java</goal>
                    </goals>
                    <configuration>
                        <mainClass>io.labs64.authcontext.openapi.OpenApiAuthPreprocessorCli</mainClass>
                        <classpathScope>compile</classpathScope>
                        <arguments>
                            <argument>--input</argument>
                            <argument>${openapi.source}</argument>
                            <argument>--openapi-output</argument>
                            <argument>${openapi.generated}</argument>
                            <argument>--cerbos-output</argument>
                            <argument>${auth-policy-cerbos.output}</argument>
                            <argument>--module</argument>
                            <argument>${auth-policy.module}</argument>
                        </arguments>
                    </configuration>
                </execution>
            </executions>
        </plugin>

        <!-- 2. OpenAPI Generator: reads the CLEANED spec -->
        <plugin>
            <groupId>org.openapitools</groupId>
            <artifactId>openapi-generator-maven-plugin</artifactId>
            <version>${openapi-generator-maven-plugin.version}</version>
            <executions>
                <execution>
                    <id>your-module-id</id>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <phase>generate-sources</phase>
                    <configuration>
                        <!-- Point to the GENERATED spec, not the source -->
                        <inputSpec>${openapi.generated}</inputSpec>
                        <generatorName>spring</generatorName>
                        <output>${project.build.directory}/generated-sources</output>
                        <apiPackage>io.labs64.yourmodule.api</apiPackage>
                        <modelPackage>io.labs64.yourmodule.model</modelPackage>
                        <generateModels>false</generateModels>
                        <generateSupportingFiles>true</generateSupportingFiles>
                        <configOptions>
                            <useTags>true</useTags>
                            <interfaceOnly>true</interfaceOnly>
                            <useSpringBoot3>true</useSpringBoot3>
                            <useJakartaEe>true</useJakartaEe>
                        </configOptions>
                    </configuration>
                </execution>
            </executions>
        </plugin>

        <!-- 3. Build helper: add generated resources to classpath -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <version>${build-helper-maven-plugin.version}</version>
            <executions>
                <execution>
                    <id>add-source</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>add-source</goal>
                    </goals>
                    <configuration>
                        <sources>
                            <source>${project.build.directory}/generated-sources</source>
                        </sources>
                    </configuration>
                </execution>
                <execution>
                    <id>add-generated-resources</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>add-resource</goal>
                    </goals>
                    <configuration>
                        <resources>
                            <resource>
                                <directory>${project.build.directory}/generated-resources</directory>
                            </resource>
                        </resources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Step 3: Verify the Build

Run `mvn clean compile` and check three outputs:

### 1. Generated Cerbos output

```bash
ls target/cerbos
# Should contain routes.yaml and Cerbos resource policies (e.g., your_module_api.yaml)
cat target/cerbos/routes.yaml
```

Should contain route entries for each endpoint:

```yaml
routes:
  /audit/publish:
    POST:
      module: auditflow
      operationId: publishEvent
      public: false
```

### 2. Generated OpenAPI with annotations

```bash
grep -A3 'x-operation-extra-annotation' target/generated-openapi/openapi-your-module.yaml
```

Should show injected annotations:

```yaml
x-operation-extra-annotation:
  - "@io.labs64.authcontext.authorization.RequireTenant"
  - "@io.labs64.authcontext.authorization.RequireScopes({\"audit-event:write\"})"
```

### 3. Generated Java interface with annotations

```bash
grep -n 'RequireTenant\|RequireScopes\|PublicEndpoint' target/generated-sources/src/main/java/io/labs64/yourmodule/api/YourApi.java
```

Should show annotations on the generated interface methods.

## Step 4: Runtime Configuration

The `auth-context-spring-boot-starter` auto-configures everything. Add this to your `application.yml`:

```yaml
labs64:
  auth-context:
    enabled: ${LABS64_AUTHCONTEXT_ENABLED:true}
    public-paths:
      - /actuator
      - /v3/api-docs
      - /swagger-ui
      - /error
      # Add any other paths that should bypass auth entirely
  tenant:
    default: ${LABS64_TENANT_DEFAULT:}  # Dev-only fallback for gateway-less runs
```

### How runtime enforcement works

1. **`AuthContextFilter`** (highest precedence): Parses `X-Auth-*` headers from the gateway. FAIL CLOSED: returns 401 on non-public paths without valid headers.

2. **`RequireTenantInterceptor`**: Checks `@RequireTenant` on generated interfaces. Returns 401 if no context, 403 if tenant is null.

3. **`RequireScopesInterceptor`**: Checks `@RequireScopes` on generated interfaces. Returns 401 if no context, 403 if required scopes are missing.

4. **`@PublicEndpoint`**: Marks the generated API method as public. The same
   preprocessor decision writes the generated `auth-public-paths` resource used
   by `AuthContextFilter`; no hand-maintained API-path entry is required.

## Policy Distribution to the Central PDP

Services **no longer serve** authorization policies at runtime (the `/.well-known/auth-policy` endpoints have been deleted). Instead, the generated Cerbos policies and route manifests are collected from the build artifacts (`target/cerbos`) and distributed to the Central PDP via a GitOps CI pipeline. 

1. **Build Time**: The OpenAPI preprocessor emits Cerbos YAML policies (`<module>_api.yaml` and `<DomainResource>.yaml`) and a `routes.yaml` manifest.
2. **CI Pipeline**: A shared script (`build-authz-policies.sh`) aggregates the `target/cerbos` directories from all modules into a centralized policy distribution format.
3. **Runtime**: The central Cerbos PDP loads these generated policies from a mounted ConfigMap. The Traefik Authproxy loads the `routes.yaml` manifests to understand the path-to-operation mappings.
4. **Enforcement**: When a request arrives, the Traefik Authproxy queries the central Cerbos PDP for edge reachability (Tier 1). Then, inside your module, the `@Authorize` interceptors from `auth-context-spring-boot-starter` query the central Cerbos PDP for fine-grained domain authorization (Tier 2).

This disconnected topology guarantees zero engine footprint inside the Java application, as the policy engine evaluates entirely out-of-process.

### Accessing the auth context in controllers

```java
import io.labs64.authcontext.core.AuthContextHolder;

// Get tenant from gateway context (returns Optional<AuthContext>)
AuthContextHolder.get().ifPresent(context -> {
    String tenantId = context.tenantId();
});

// Require context (throws IllegalStateException if absent)
AuthContext context = AuthContextHolder.require();
String userId = context.userId();
String tenantId = context.tenantId();
Set<String> scopes = context.scopes();
```

## Step 5: Local Development

For gateway-less local runs, disable the auth filter and use a fixed tenant:

```yaml
# application-local.yml
labs64:
  auth-context:
    enabled: false
  tenant:
    default: t_dev
```

## Complete Example: AuditFlow

The auditflow module was integrated as follows:

### OpenAPI spec (`openapi-audit-v1.yaml`)

```yaml
paths:
  /audit/publish:
    post:
      operationId: publishEvent
      security:
        - oauth:
            - audit-event:write
      x-labs64:
        auth:
          tenant: true
```

### Generated outputs

**Generated Cerbos edge policy (`target/cerbos/auditflow_api.yaml`)**:
```yaml
apiVersion: api.cerbos.dev/v1
resourcePolicy:
  version: default
  resource: auditflow_api
  rules:
    - actions: ["publishEvent"]
      effect: EFFECT_ALLOW
      roles: ["*"]
      condition:
        match:
          all:
            of:
              - expr: has(request.principal.attr.tenant)
              - expr: '"audit-event:write" in request.principal.attr.scopes'
```

**Generated AuditEventApi.java**:
```java
@RequireTenant
@RequireScopes({"audit-event:write"})
ResponseEntity<String> publishEvent(@Valid AuditEvent event);
```

### Controller usage

```java
@Override
public ResponseEntity<String> publishEvent(@Valid AuditEvent event) {
    // Tenant is already validated by RequireTenantInterceptor
    AuthContextHolder.get().ifPresent(context -> {
        if (context.tenantId() != null) {
            event.setTenantId(context.tenantId());
        }
    });
    // ...
}
```

## Troubleshooting

### Preprocessor fails with "package does not exist"

Ensure `jackson-dataformat-yaml` is in your dependencies (Step 2b).

### Generated API has no annotations

Check that `${openapi.generated}` is used as `inputSpec` in the OpenAPI generator, not `${openapi.source}`.

### 401 on all endpoints

Add your paths to `labs64.auth-context.public-paths` or ensure the gateway sends valid `X-Auth-*` headers.

### Annotations not enforced

Ensure `auth-context-spring-boot-starter` is on the classpath and `labs64.auth-context.enabled` is not set to `false`.

## Reading @Authorize enforcement logs

Each `@Authorize` decision emits a non-sensitive summary on `io.labs64.authcontext.cerbos.LoggingDecisionListener`:

```
cerbos-domain outcome=<enforced|shadow>-<allow|deny> decision=<allow|deny|error> \
  mode=<enforce|shadow> action=<a> resourceType=<t> reasons=<policyIds|-> requestId=<id>
```

INFO for a clean allow, WARN for deny/error.

Sensitive fields (user, tenant, resolved resource id, raw error) ride the dedicated `io.labs64.authcontext.cerbos.detail` logger at DEBUG, off by default. Enable it during the Cerbos testing phase, e.g. in `application.yaml`:

```yaml
logging:
  level:
    io.labs64.authcontext.cerbos.detail: DEBUG
```

The detail line emits `cerbos-detail requestId=<id> user=<user> tenant=<tenant> resource=<type>::<id>[ error=<err>]`, shareable with the summary for joining via `requestId`.
