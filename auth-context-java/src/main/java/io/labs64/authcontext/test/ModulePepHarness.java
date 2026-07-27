package io.labs64.authcontext.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.authcontext.core.AuthContextParser;
import io.labs64.authcontext.web.AuthContextFilter;
import io.labs64.authcontext.web.AuthContextProperties;
import io.labs64.authcontext.web.PublicPathMatcher;

/**
 * Builds a {@link MockMvc} that puts a module's <em>real</em> controllers behind
 * its <em>real</em> {@link AuthContextFilter}, for use with
 * {@link AuthEnforcementContract}.
 *
 * <p>Two properties make the resulting test meaningful rather than circular:
 *
 * <ul>
 * <li><b>Nothing restates production configuration.</b> The filter's properties
 * are bound from the module's own {@code application.yml} exactly as Spring
 * binds them, and its public operations come from the build-generated
 * {@code auth-public-paths}. An over-broad {@code public-paths} prefix or a
 * drifted public-path list therefore fails the test instead of being copied
 * into it.</li>
 * <li><b>Controllers are discovered, not enumerated.</b> Every
 * {@link RestController} under the given base package is instantiated with
 * Mockito mocks for its constructor dependencies, so a new controller cannot
 * quietly escape coverage.</li>
 * </ul>
 *
 * <p>The collaborators are mocks on purpose: a protected request must be refused
 * before any of them is reached, so what they would have returned is irrelevant.
 *
 * <p>This covers the module-layer PEP only. The gateway edge (traefik-authproxy
 * + Cerbos) is a separate layer and needs its own proof.
 */
public final class ModulePepHarness {

    private ModulePepHarness() {
    }

    /**
     * A {@code MockMvc} over every {@code @RestController} in {@code basePackage},
     * filtered through the module's production auth-context filter.
     *
     * @param basePackage package to scan, e.g. {@code io.labs64.paymentgateway.controller}
     */
    public static MockMvc withProductionAuthFilter(final String basePackage) {
        return MockMvcBuilders.standaloneSetup(controllers(basePackage).toArray())
                .addFilters(productionAuthContextFilter())
                .build();
    }

    /** The filter exactly as the module's autoconfiguration builds it in production. */
    public static AuthContextFilter productionAuthContextFilter() {
        return new AuthContextFilter(productionProperties(), new AuthContextParser(), generatedPublicPaths());
    }

    /**
     * {@code labs64.auth-context.*} bound from the module's own
     * {@code application.yml} — the real values, not a copy of them.
     */
    public static AuthContextProperties productionProperties() {
        try {
            MockEnvironment environment = new MockEnvironment();
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));
            sources.forEach(source -> environment.getPropertySources().addLast(source));
            return Binder.get(environment)
                    .bind("labs64.auth-context", AuthContextProperties.class)
                    .orElseGet(AuthContextProperties::new);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the module's application.yml", e);
        }
    }

    /**
     * The generated public-operation list, or an empty matcher when the module
     * declares no public operations — mirroring the production fallback in
     * {@code AuthContextAutoConfiguration} rather than assuming a list exists.
     */
    public static PublicPathMatcher generatedPublicPaths() {
        Resource resource = new ClassPathResource("auth-public-paths");
        if (!resource.exists()) {
            return PublicPathMatcher.empty();
        }
        try {
            return PublicPathMatcher.fromLines(resource.getContentAsString(StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the generated auth-public-paths", e);
        }
    }

    /**
     * Instantiates every {@code @RestController} in the package with mocked
     * constructor dependencies.
     */
    public static List<Object> controllers(final String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No @RestController found under " + basePackage
                    + ". An auth-enforcement suite with no controllers behind it proves nothing.");
        }
        List<Object> controllers = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            controllers.add(instantiate(candidate.getBeanClassName()));
        }
        return controllers;
    }

    private static Object instantiate(final String className) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> constructor = widestConstructor(type);
            Object[] arguments = new Object[constructor.getParameterCount()];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = Mockito.mock(parameterTypes[i]);
            }
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate controller " + className
                    + " for the auth-enforcement suite", e);
        }
    }

    /**
     * Lombok's {@code @RequiredArgsConstructor} emits a single all-args
     * constructor; picking the widest one also works for hand-written
     * controllers that keep a convenience constructor alongside it.
     */
    private static Constructor<?> widestConstructor(final Class<?> type) {
        Constructor<?> widest = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (widest == null || constructor.getParameterCount() > widest.getParameterCount()) {
                widest = constructor;
            }
        }
        if (widest == null) {
            throw new IllegalStateException("No constructor on " + type.getName());
        }
        return widest;
    }
}
