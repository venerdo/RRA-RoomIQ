package rw.rra.roomiq.organization.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OrganizationAuthorizationWebConfiguration implements WebMvcConfigurer {
    private final OrganizationAuthorizationInterceptor authorizationInterceptor;

    public OrganizationAuthorizationWebConfiguration(
            OrganizationAuthorizationInterceptor authorizationInterceptor) {
        this.authorizationInterceptor = authorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor).addPathPatterns("/api/v1/**");
    }
}