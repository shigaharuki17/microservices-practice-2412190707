package com.zjgsu.hly.security;
import feign.RequestInterceptor;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class InternalFeignConfiguration {
    @Bean RequestInterceptor internalAuthentication(@Value("${INTERNAL_API_KEY:}") String key) {
        return request -> {if(!key.isBlank()) {
            request.header("X-Internal-API-Key",key);
            request.header("X-Authenticated-User","repair-service");
            request.header("X-Authenticated-Role","SERVICE");
        }};
    }
}
