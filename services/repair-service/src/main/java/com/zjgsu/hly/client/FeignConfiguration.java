package com.zjgsu.hly.client;
import feign.RequestInterceptor;
import feign.ResponseInterceptor;
import feign.Retryer;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfiguration {
    @Bean public Retryer retryer() { return Retryer.NEVER_RETRY; }
    @Bean public RequestInterceptor requestTrace() {
        return template -> {
            if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String trace=attrs.getRequest().getHeader("X-Request-ID");
                if(trace!=null && trace.matches("[A-Za-z0-9_-]{1,64}")) template.header("X-Request-ID",trace);
            }
        };
    }
    @Bean public ResponseInterceptor responseTrace() {
        return (context, chain) -> {
            // Instance ID comes from the actual callee response, not the caller's selection guess.
            var response=context.response();
            response.headers().forEach((name, values) -> {
                if(name.equalsIgnoreCase("X-Instance-ID")) for(String instance:values) {
                    if(instance.matches("[A-Za-z0-9_.-]{1,80}")) {
                        LoggerFactory.getLogger(FeignConfiguration.class).info(
                            "FEIGN_RESPONSE instance={} status={} path={}",instance,response.status(),response.request().url());
                        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                                && attrs.getResponse()!=null)
                            attrs.getResponse().addHeader("X-Dependency-Instance",instance);
                    }
                }
            });
            return chain.next(context);
        };
    }
}
