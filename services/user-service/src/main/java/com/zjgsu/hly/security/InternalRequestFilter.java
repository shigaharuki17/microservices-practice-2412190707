package com.zjgsu.hly.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
@ConditionalOnProperty(name="app.internal-security.enabled",havingValue="true",matchIfMissing=true)
public class InternalRequestFilter extends OncePerRequestFilter {
    private final byte[] key;
    public InternalRequestFilter(@Value("${INTERNAL_API_KEY:}") String secret) {
        if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("INTERNAL_API_KEY must be >=32 bytes");
        key=secret.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path=request.getServletPath();
        if(path.startsWith("/api/")) {
            String supplied=request.getHeader("X-Internal-API-Key");
            if(supplied==null || !MessageDigest.isEqual(key,supplied.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(403);response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Direct backend access is forbidden; use the gateway\"}");return;
            }
        }
        chain.doFilter(request,response);
    }
}
