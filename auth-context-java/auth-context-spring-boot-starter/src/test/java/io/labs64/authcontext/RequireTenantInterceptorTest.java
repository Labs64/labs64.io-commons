package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import io.labs64.authcontext.authorization.RequireTenant;
import io.labs64.authcontext.authorization.RequireTenantInterceptor;

class RequireTenantInterceptorTest {

    private final RequireTenantInterceptor interceptor = new RequireTenantInterceptor();

    @AfterEach
    void cleanup() {
        AuthContextHolder.clear();
    }

    @Test
    void allowsTenantScopedContext() throws Exception {
        AuthContextHolder.set(new AuthContext("jdoe", "t_1", Set.of(), "r-1"));

        assertThat(invoke(Controller.class, "operation")).isTrue();
    }

    @Test
    void forbidsTenantlessContext() throws Exception {
        AuthContextHolder.set(new AuthContext("svc:scheduler", null, Set.of(), "r-1"));

        assertThat(invoke(Controller.class, "operation")).isFalse();
    }

    @Test
    void honorsInterfaceMethodAnnotation() throws Exception {
        AuthContextHolder.set(new AuthContext("jdoe", "t_1", Set.of(), "r-1"));

        assertThat(invoke(GeneratedApiController.class, "operation")).isTrue();
    }

    private boolean invoke(final Class<?> controller, final String methodName) throws Exception {
        final Method method = controller.getDeclaredMethod(methodName);
        final HandlerMethod handler = new HandlerMethod(controller.getDeclaredConstructor().newInstance(), method);
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);
    }

    static class Controller {
        @RequireTenant
        public void operation() {
        }
    }

    interface GeneratedApi {
        @RequireTenant
        void operation();
    }

    static class GeneratedApiController implements GeneratedApi {
        @Override
        public void operation() {
        }
    }
}
