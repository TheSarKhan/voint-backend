package com.starsoft.voint.rbac;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;
    private final com.starsoft.voint.approval.ApprovalGateInterceptor approvalGateInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Order matters and is the whole design: permission first, approval second. You cannot
        // queue up an operation you were never allowed to perform - the approval queue is a second
        // gate, not a way of asking someone else to get you past the first.
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**").order(1);
        registry.addInterceptor(approvalGateInterceptor).addPathPatterns("/api/**").order(2);
    }
}
