package com.stocket.system.internal.tracing;

import com.stocket.identity.CurrentHouseholdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnBean(CurrentHouseholdProvider.class)
class WriteAuditConfiguration implements WebMvcConfigurer {
    private final WriteAuditInterceptor interceptor;
    WriteAuditConfiguration(WriteAuditInterceptor interceptor) { this.interceptor = interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**"); }
}
