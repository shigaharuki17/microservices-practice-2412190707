package com.zjgsu.hly.auth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
public class AuthController {
    private final AuthService auth;
    private final LoginAccountRepository accounts;
    public AuthController(AuthService auth,LoginAccountRepository accounts){this.auth=auth;this.accounts=accounts;}
    public record Credentials(String username,String password) {}
    @PostMapping("/api/auth/login")
    public AuthService.TokenResponse login(@RequestBody Credentials body) {return auth.login(body.username(),body.password());}
    @GetMapping("/api/auth/me")
    public Map<String,String> me(HttpServletRequest request) {
        return Map.of("username",request.getHeader("X-Authenticated-User"),"role",request.getHeader("X-Authenticated-Role"));
    }
    @GetMapping("/api/admin/accounts-summary")
    public Map<String,Object> summary(HttpServletRequest request) {
        if(!"ADMIN".equals(request.getHeader("X-Authenticated-Role")))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return Map.of("accountCount",accounts.count(),"viewer",request.getHeader("X-Authenticated-User"));
    }
}
