package io.labs64.authcontext.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import io.labs64.authcontext.UserContext;
import io.labs64.authcontext.UserContextHolder;

/** Resolves {@link UserContext} controller parameters from the bound request context. */
public class UserContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return UserContext.class.isAssignableFrom(parameter.getParameterType())
                || parameter.hasParameterAnnotation(CurrentUserContext.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return UserContextHolder.require();
    }
}
