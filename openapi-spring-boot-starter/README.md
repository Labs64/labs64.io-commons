# Labs64.IO OpenAPI Spring Boot Starter

Shared runtime OpenAPI and Swagger UI configuration for Labs64.IO servlet applications.

## Dependency

```xml
<dependency>
    <groupId>io.labs64</groupId>
    <artifactId>openapi-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Configuration

```yaml
labs64:
  openapi:
    spec-location: classpath:openapi/openapi-service-v1.yaml
    gateway-path: /service/api/v1
    local-path: /
    security:
      enabled: true
      scheme-name: bearerAuth
```

The starter configures the runtime server list, bearer security scheme and API metadata exposed by springdoc. API metadata is read from the `info` section of the configured canonical OpenAPI document.
