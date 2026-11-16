package com.zjgsu.hly.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final LoginAccountRepository accounts;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final JwtEncoder encoder;
    private final String dummyHash=passwords.encode("nonexistent-account-check");
    public AuthService(LoginAccountRepository accounts,@Value("${auth.jwt-secret}") String secret) {
        this.accounts=accounts;
        if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("JWT_SECRET must be >=32 bytes");
        encoder=new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8)));
    }
    public String hash(String password) { return passwords.encode(password); }
    public TokenResponse login(String username,String password) {
        if(username==null || password==null || username.length()>80 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid username or password");
        var account=accounts.findByUsername(username).orElse(null);
        boolean matches=passwords.matches(password,account==null?dummyHash:account.passwordHash);
        if(account==null || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid username or password");
        var now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer("https://community-repair.local").subject(account.username)
            .audience(List.of("community-api")).issuedAt(now).expiresAt(now.plusSeconds(1800))
            .claim("roles",List.of(account.role)).build();
        var header=JwsHeader.with(MacAlgorithm.HS256).build();
        String token=encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();
        return new TokenResponse(token,"Bearer",1800,account.username,account.role);
    }
    public record TokenResponse(String accessToken,String tokenType,int expiresIn,String username,String role) {}
}
