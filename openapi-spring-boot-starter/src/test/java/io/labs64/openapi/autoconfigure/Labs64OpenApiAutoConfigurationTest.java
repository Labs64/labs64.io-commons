package io.labs64.openapi.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class Labs64OpenApiAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Labs64OpenApiAutoConfiguration.class))
            .withPropertyValues(
                    "labs64.openapi.spec-location=classpath:openapi/test-api.yaml",
                    "labs64.openapi.gateway-path=/test/api/v1");

    @Test
    void configuresRuntimeOpenApiDocument() {
        contextRunner.run(context -> {
            OpenAPI openApi = new OpenAPI();
            context.getBean("labs64OpenApiCustomizer", OpenApiCustomizer.class).customise(openApi);

            assertThat(openApi.getServers()).extracting("url")
                    .containsExactly("/test/api/v1", "/");
            assertThat(openApi.getInfo().getTitle()).isEqualTo("Test API");
            assertThat(openApi.getInfo().getVersion()).isEqualTo("1.2.3");
            assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
            assertThat(openApi.getSecurity().get(0)).containsKey("bearerAuth");
        });
    }

    @Test
    void canBeDisabled() {
        contextRunner.withPropertyValues("labs64.openapi.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("labs64OpenApiCustomizer"));
    }
}
