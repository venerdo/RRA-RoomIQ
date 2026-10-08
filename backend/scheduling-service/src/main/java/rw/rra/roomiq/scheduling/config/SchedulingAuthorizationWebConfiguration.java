package rw.rra.roomiq.scheduling.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SchedulingAuthorizationWebConfiguration implements WebMvcConfigurer {
    private final SchedulingAuthorizationInterceptor authorizationInterceptor;

    public SchedulingAuthorizationWebConfiguration(SchedulingAuthorizationInterceptor authorizationInterceptor) {
        this.authorizationInterceptor = authorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor).addPathPatterns("/api/v1/**");
    }
}