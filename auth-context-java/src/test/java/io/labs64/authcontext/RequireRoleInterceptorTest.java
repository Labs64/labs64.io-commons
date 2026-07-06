package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import io.labs64.authcontext.web.RequireRole;
import io.labs64.authcontext.web.RequireRoleInterceptor;

class RequireRoleInterceptorTest {

    private final RequireRoleInterceptor interceptor = new RequireRoleInterceptor();

    static class TestController {
        @RequireRole({ "admin-role", "ecommerce-role" })
        public void protectedOperation() {
        }

        public void openOperation() {
        }
    }

    @RequireRole("admin-role")
    static class ClassLevelController {
        public void operation() {
        }
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    private boolean invoke(Class<?> controller, String methodName) throws Exception {
        Method method = controller.getDeclaredMethod(methodName);
        HandlerMethod handler = new HandlerMethod(controller.getDeclaredConstructor().newInstance(), method);
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);
    }

    @Test
    void allowsCallerWithMatchingRole() throws Exception {
        UserContextHolder.set(new UserContext("jdoe", "t_1", Set.of("ecommerce-role"), "r-1"));
        assertThat(invoke(TestController.class, "protectedOperation")).isTrue();
    }

    @Test
    void forbidsCallerWithoutMatchingRole() throws Exception {
        UserContextHolder.set(new UserContext("jdoe", "t_1", Set.of("other-role"), "r-1"));
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
        UserContextHolder.set(new UserContext("jdoe", "t_1", Set.of("other-role"), "r-1"));
        assertThat(invoke(ClassLevelController.class, "operation")).isFalse();
        UserContextHolder.set(new UserContext("jdoe", "t_1", Set.of("admin-role"), "r-1"));
        assertThat(invoke(ClassLevelController.class, "operation")).isTrue();
    }
}
