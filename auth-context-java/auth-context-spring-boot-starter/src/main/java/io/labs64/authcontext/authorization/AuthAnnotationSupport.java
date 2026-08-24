package io.labs64.authcontext.authorization;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

public final class AuthAnnotationSupport {

    private AuthAnnotationSupport() {
    }

    public static <A extends Annotation> A find(final HandlerMethod handlerMethod, final Class<A> annotationType) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), annotationType);
        if (annotation != null) {
            return annotation;
        }

        annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), annotationType);
        if (annotation != null) {
            return annotation;
        }

        for (Class<?> interfaceType : handlerMethod.getBeanType().getInterfaces()) {
            annotation = findOnInterface(handlerMethod.getMethod(), interfaceType, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }

        return null;
    }

    private static <A extends Annotation> A findOnInterface(
            final Method implementationMethod,
            final Class<?> interfaceType,
            final Class<A> annotationType) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(interfaceType, annotationType);
        if (annotation != null) {
            return annotation;
        }

        try {
            final Method interfaceMethod = interfaceType.getMethod(
                    implementationMethod.getName(),
                    implementationMethod.getParameterTypes());
            return AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, annotationType);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
