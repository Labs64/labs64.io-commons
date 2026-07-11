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

import io.labs64.authcontext.authorization.RequireScopes;
import io.labs64.authcontext.authorization.RequireScopesInterceptor;

class RequireScopesInterceptorTest {

    private final RequireScopesInterceptor interceptor = new RequireScopesInterceptor();

    static class TestController {
        @RequireScopes({ "account:read", "ecommerce:read" })
        public void protectedOperation() {
        }

        public void openOperation() {
        }
    }

    @RequireScopes("account:read")
    static class ClassLevelController {
        public void operation() {
        }
    }

    @AfterEach
    void cleanup() {
        AuthContextHolder.clear();
    }

    private boolean invoke(Class<?> controller, String methodName) throws Exception {
        Method method = controller.getDeclaredMethod(methodName);
        HandlerMethod handler = new HandlerMethod(controller.getDeclaredConstructor().newInstance(), method);
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);
    }

    @Test
    void allowsCallerWithMatchingScope() throws Exception {
        AuthContextHolder.set(new AuthContext("jdoe", "t_1", Set.of("ecommerce:read"), "r-1"));
        assertThat(invoke(TestController.class, "protectedOperation")).isTrue();
    }

    @Test
    void forbidsCallerWithoutMatchingScope() throws Exception {
        AuthContextHolder.set(new AuthContext("jdoe", "t_1", Set.of("other-scope"), "r-1"));
        assertThat(invoke(TestController.class, "protectedOperation")).isFalse();
    }

    @Test
    void unauthorizedWhenNoContextBound() throws Exception {
        assertThat(invoke(TestController.class, "protectedOperation")).isFalse();
    }

    @Test
    void ignoresUnannotatedHandlers() throws Exception {
        assertThat(invoke(TestController.class, "openOperation")).isTrue();
    }

    @Test
    void honorsClassLevelAnnotation() throws Exception {
        AuthContextHolder.set(new AuthContext("jdoe", "t_1", Set.of("other-scope"), "r-1"));
        assertThat(invoke(ClassLevelController.class, "operation")).isFalse();
        AuthContextHolder.set(new AuthContext("jdoe", "t_1", Set.of("account:read"), "r-1"));
        assertThat(invoke(ClassLevelController.class, "operation")).isTrue();
    }

    @Test
    void honorsInterfaceMethodAnnotation() throws Exception {
        AuthContextHolder.set(new AuthContext("jdoe", "t_1", Set.of("payment:write"), "r-1"));

        assertThat(invoke(GeneratedApiController.class, "pay")).isTrue();
    }

    interface GeneratedApi {
        @RequireScopes("payment:write")
        void pay();
    }

    static class GeneratedApiController implements GeneratedApi {
        @Override
        public void pay() {
        }
    }
}

