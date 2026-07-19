# Integrating `x-labs64-auth` into a Labs64.IO Module

This guide explains how to add OpenAPI-driven authorization policies to any Java backend module using the `x-labs64-auth` extension and `OpenApiAuthPreprocessorCli`.

## How It Works

The pipeline has four stages:

```
OpenAPI spec (with x-labs64-auth)
  → OpenApiAuthPreprocessorCli (build-time)
    → Cleaned OpenAPI (annotations injected)
    → auth-policy.cerbos (gateway route policy)
  → openapi-generator (generates Java interfaces with annotations)
  → auth-context-spring-boot-starter (runtime enforcement)
  → GET /.well-known/auth-policy + /.well-known/auth-policy.cerbos (runtime, served by the starter for the gateway ACS)
```

1. **Author**: Add `x-labs64-auth` to each endpoint in your OpenAPI spec
2. **Build-time**: The preprocessor strips the extension, injects Java annotations (`@RequireTenant`, `@RequireScopes`, `@PublicEndpoint`) into the generated OpenAPI, and emits `auth-policy.cerbos`
3. **Code generation**: OpenAPI Generator produces Java API interfaces with the injected annotations
4. **Runtime**: `auth-context-spring-boot-starter` auto-configures filters and interceptors that enforce the annotations

## Step 1: Add `x-labs64-auth` to Your OpenAPI Spec

Add the `x-labs64-auth` extension to each operation (endpoint). The extension supports three modes:

### Require tenant + scopes (authenticated endpoint)

```yaml
paths:
  /my-resource:
    get:
      operationId: listMyResources
      x-labs64-auth:
        tenant: true
        scopes:
          - my-resource:read
```

### Require tenant only (no scope check)

```yaml
paths:
  /my-resource:
    post:
      operationId: createMyResource
      x-labs64-auth:
        tenant: true
```

### Public endpoint (no auth required)

```yaml
paths:
  /health:
    get:
      operationId: healthCheck
      x-labs64-auth:
        public: true
```

### Path-level defaults

You can set `x-labs64-auth` at the path level to apply to all operations under that path. Operation-level overrides path-level:

```yaml
paths:
  /my-resource:
    x-labs64-auth:
      tenant: true
      scopes:
        - my-resource:read
    get:
      operationId: listMyResources
      # Inherits: tenant=true, scopes=[my-resource:read]
    post:
      operationId: createMyResource
      x-labs64-auth:
        tenant: true
        scopes:
          - my-resource:write
      # Overrides: scopes changed to my-resource:write
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
      x-labs64-auth:
        tenant: true
        scopes:
          - payment:pay
        resource: Payment        # → generated domain permit + tenant guard
```

The resource `<Type>` must be declared in the shared schema
(`labs64.io-commons/auth-policy-cerbos/schema.cerbosschema`), and the module
supplies the resource's `tenant` at request time via a `CerbosEntityResolver`.
Fine-grained resource-attribute rules (workflow status, ownership) are **not**
expressible from OpenAPI — they stay in the service layer (and later Postgres
RLS), by design.

### Mutual exclusion rules

- `public: true` cannot coexist with `tenant`, `scopes`, or `resource`
- If none of `public`, `tenant`, or `scopes` are set, the endpoint is treated as public

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
    <auth-policy.generated>${project.build.directory}/generated-resources/auth-policy.cerbos</auth-policy.generated>
    <!-- Cerbos policies generated from x-labs64-auth.
         auth-policy.module MUST equal the module's gateway path prefix (e.g.
         payment-gateway) — it is baked into the Cerbos resource ids.
         edge   → auth-policy.cerbos        (Tier 1, served to the ACS/traefik)
         domain → auth-policy-domain.cerbos (Tier 2, module @Authorize PEP) -->
    <auth-policy-cerbos.generated>${project.build.directory}/generated-resources/auth-policy.cerbos</auth-policy-cerbos.generated>
    <auth-policy-cerbos-domain.generated>${project.build.directory}/generated-resources/auth-policy-domain.cerbos</auth-policy-cerbos-domain.generated>
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
                            <argument>${auth-policy-cerbos.generated}</argument>
                            <argument>--cerbos-domain-output</argument>
                            <argument>${auth-policy-cerbos-domain.generated}</argument>
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

        <!-- 3. Build helper: add generated resources (auth-policy.cerbos) to classpath -->
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

### 1. Generated auth-policy.cerbos

```bash
cat target/generated-resources/auth-policy.cerbos
```

Should contain route entries for each endpoint:

```json
{
  "version": 1,
  "routes": [
    {
      "operationId": "publishEvent",
      "method": "POST",
      "path": "/audit/publish",
      "public": false,
      "tenantRequired": true,
      "scopes": ["audit-event:write"]
    }
  ]
}
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

4. **`@PublicEndpoint`**: Bypasses both interceptors. The path must also be in `labs64.auth-context.public-paths` to pass the filter.

## The `/.well-known/auth-policy` endpoints

When `auth-policy.cerbos` is on the classpath (Step 2c's `add-resource`), the
starter auto-registers a controller serving it verbatim at
`GET /.well-known/auth-policy`. The gateway's traefik-authproxy discovers
modules via the `labs64.io/auth-policy=true` Service label and fetches this
endpoint in-cluster to build its edge authorization table.

The same controller serves the build-generated **Tier-1 edge** Cerbos policy set
(`auth-policy.cerbos`, emitted when the preprocessor is invoked with
`--cerbos-output` + `--module`) at `GET /.well-known/auth-policy.cerbos`
(text/plain; 404 when the module does not generate it). Each generated permit
also carries `@path`/`@method`/`@public`/`@tenantRequired`/`@scopes`
annotations — the same fields `auth-policy.cerbos`'s routes carry — so this one
file doubles as the OpenAPI-template routing table. **The traefik-authproxy's
live-discovery path fetches only `auth-policy.cerbos`** (not the JSON document)
and rebuilds its routing table from those annotations
(`policy_store.parse_cerbos_document`), then evaluates it for the
authorization decision according to `CEDAR_MODE`. `auth-policy.cerbos` is still
served and still on the classpath — it remains the routing source for the
**signed-bundle** policy path (`policy_bundle.py`, which packages
`modules/<name>.json` today) — but a module integrating only for live
discovery must serve `auth-policy.cerbos` for the ACS to route to it at all.
The `--module` name must equal the module's gateway path prefix (e.g.
`payment-gateway`), because it is baked into the Cerbos resource ids.

The **Tier-2 domain** set (`auth-policy-domain.cerbos`, from
`--cerbos-domain-output`) is **not** served over the well-known endpoint — it is
consumed only in-process by this module's `@Authorize` PEP from the classpath
(`labs64.auth.cerbos.policy-location`, default `classpath:auth-policy-domain.cerbos`).
Both files are generated from the one `x-labs64-auth` source, so traefik and the
module enforce the same OpenAPI-derived contract.

- Both paths are **unconditionally public** in `AuthContextFilter` — they
  cannot be disabled via `public-paths`, because the ACS must reach them
  before it can authorize anything.
- It is **not** exposed through the external gateway: module IngressRoutes
  only publish API prefixes. The policy content is derived from the OpenAPI
  spec (whose `/v3/api-docs` is public), so no secrets are involved.
- Scope matching at the edge and in `RequireScopesInterceptor` is **OR**
  (any listed scope suffices). The edge tenant check is presence-only —
  tenant validation stays a module concern via `AuthContext.tenantId()`.

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
      x-labs64-auth:
        tenant: true
        scopes:
          - audit-event:write
```

### Generated outputs

**auth-policy.cerbos** (signed-bundle routing source):
```json
{
  "version": 1,
  "routes": [{
    "operationId": "publishEvent",
    "method": "POST",
    "path": "/audit/publish",
    "public": false,
    "tenantRequired": true,
    "scopes": ["audit-event:write"]
  }]
}
```

**auth-policy.cerbos** (live-discovery routing + decision source — same fields, as annotations):
```
@id("auditflow::publishEvent")
@path("/audit/publish")
@method("POST")
@public("false")
@tenantRequired("true")
@scopes("audit-event:write")
permit(
  principal,
  action == Labs64IO::Action::"invoke",
  resource == Labs64IO::ApiOperation::"auditflow::publishEvent"
) when { (context has tenant) && (context.scopes.contains("audit-event:write")) };
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
