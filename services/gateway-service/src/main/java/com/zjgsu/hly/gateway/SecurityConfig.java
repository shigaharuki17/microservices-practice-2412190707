package com.zjgsu.hly.gateway;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
@Configuration
public class SecurityConfig {
    @Bean SecurityWebFilterChain security(ServerHttpSecurity http) {
        var roles=new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");roles.setAuthorityPrefix("ROLE_");
        var converter=new JwtAuthenticationConverter();converter.setJwtGrantedAuthoritiesConverter(roles);
        return http.csrf(csrf->csrf.disable())
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange(auth->auth
                .pathMatchers(HttpMethod.POST,"/api/auth/login").permitAll()
                .pathMatchers(HttpMethod.GET,"/","/index.html","/app.js","/app.css","/actuator/health").permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.DELETE,"/api/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.POST,"/api/repair-orders/*/review","/api/repair-orders/*/assign",
                    "/api/repair-orders/*/accept","/api/portal/**","/api/residents/**","/api/technicians/**").hasRole("ADMIN")
                .pathMatchers("/api/**").authenticated().anyExchange().denyAll())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter))))
            .build();
    }
    @Bean ReactiveJwtDecoder jwtDecoder(@Value("${JWT_SECRET}") String secret) {
        if(secret.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalArgumentException("JWT_SECRET must be >=32 bytes");
        var key=new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
        var decoder=NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("https://community-repair.local"),
            new JwtClaimValidator<java.util.List<String>>("aud",aud->aud!=null&&aud.contains("community-api")),
            new JwtClaimValidator<java.time.Instant>("exp",exp->exp!=null)));
        return decoder;
    }
}
