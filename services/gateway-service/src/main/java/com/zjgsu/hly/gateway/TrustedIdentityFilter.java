package com.zjgsu.hly.gateway;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
@Component
public class TrustedIdentityFilter implements GlobalFilter,Ordered {
    private final String key;
    public TrustedIdentityFilter(@Value("${INTERNAL_API_KEY}") String key) {
        if(key.length()<32)throw new IllegalArgumentException("INTERNAL_API_KEY must be >=32 bytes");this.key=key;
    }
    public int getOrder(){return -1;}
    public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){
        return exchange.getPrincipal().ofType(JwtAuthenticationToken.class)
            .map(jwt->sanitize(exchange,jwt)).defaultIfEmpty(sanitize(exchange,null)).flatMap(chain::filter);
    }
    private ServerWebExchange sanitize(ServerWebExchange exchange,JwtAuthenticationToken jwt){
        var request=exchange.getRequest().mutate().headers(headers->{
            for(String name:List.of("X-User-Id","X-User-Name","X-Username","X-Role","X-Roles",
                    "X-Authenticated-User","X-Authenticated-Role","X-Internal-API-Key"))headers.remove(name);
            headers.remove("Authorization"); // downstream receives server-authenticated identity only
            headers.set("X-Internal-API-Key",key);
            if(jwt!=null){
                headers.set("X-Authenticated-User",jwt.getName());
                headers.set("X-Authenticated-Role",String.join(",",jwt.getToken().getClaimAsStringList("roles")));
            }
        }).build();
        return exchange.mutate().request(request).build();
    }
}
