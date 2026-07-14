package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;

import io.labs64.authcontext.autoconfigure.AuthContextAutoConfiguration;
import io.labs64.authcontext.web.AuthContextWebMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class AuthContextAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(ApplicationWebConfiguration.class)
            .withConfiguration(AutoConfigurations.of(AuthContextAutoConfiguration.class));

    @Test
    void registersAuthConfigurerAlongsideApplicationConfigurer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthContextWebMvcConfigurer.class);
            assertThat(context).getBeans(WebMvcConfigurer.class).hasSize(2);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationWebConfiguration {

        @Bean
        WebMvcConfigurer applicationWebMvcConfigurer() {
            return new WebMvcConfigurer() {
            };
        }
    }
}
