package com.zjgsu.hly.web;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InstanceTraceFilter extends OncePerRequestFilter {
    private final String instance;
    public InstanceTraceFilter(@Value("${INSTANCE_ID:${HOSTNAME:user-local}}") String instance) { this.instance=instance; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
            FilterChain chain) throws ServletException,IOException {
        response.setHeader("X-Instance-ID",instance);
        try { chain.doFilter(request,response); }
        finally {
            if(request.getRequestURI().startsWith("/api/residents"))
                LoggerFactory.getLogger(InstanceTraceFilter.class).info("BUSINESS_HTTP instance={} method={} path={} status={}",
                    instance,request.getMethod(),request.getRequestURI(),response.getStatus());
        }
    }
}
