package io.labs64.authcontext.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.labs64.authcontext.web.PublicPathMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * The generated public-paths resource must be loaded into the
 * {@link PublicPathMatcher} bean and honored by the filter's fail-closed gate,
 * with a safe empty fallback when the resource is absent.
 */
class AuthContextAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthContextAutoConfiguration.class));

    @Test
    void loadsGeneratedPublicPathsFromClasspathResource() {
        runner.withPropertyValues("labs64.auth-context.public-paths-location=classpath:test-auth-public-paths")
                .run(context -> {
                    assertThat(context).hasSingleBean(PublicPathMatcher.class);
                    PublicPathMatcher matcher = context.getBean(PublicPathMatcher.class);
                    assertThat(matcher.matches("GET", "/payment-definitions")).isTrue();
                    assertThat(matcher.matches("POST", "/providers/stripe/webhooks")).isTrue();
                    assertThat(matcher.matches("GET", "/payments")).isFalse();
                });
    }

    @Test
    void fallsBackToEmptyMatcherWhenResourceAbsent() {
        runner.withPropertyValues("labs64.auth-context.public-paths-location=classpath:does-not-exist")
                .run(context -> {
                    assertThat(context).hasSingleBean(PublicPathMatcher.class);
                    assertThat(context.getBean(PublicPathMatcher.class).matches("GET", "/payment-definitions"))
                            .isFalse();
                });
    }
}
