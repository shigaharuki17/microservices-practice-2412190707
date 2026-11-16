package com.zjgsu.hly.gateway;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class GatewaySecurityTests {
 final String key="unit-test-only-signing-key-not-for-deployment-123456";
 String signed(String issuer,List<String> audience,Instant expires,String signingKey)throws Exception {
  var claims=new JWTClaimsSet.Builder().subject("resident").issuer(issuer).audience(audience)
   .claim("roles",List.of("RESIDENT")).issueTime(Date.from(Instant.now().minusSeconds(1200)));
  if(expires!=null)claims.expirationTime(Date.from(expires));
  var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims.build());jwt.sign(new MACSigner(signingKey));return jwt.serialize();
 }
 @Test void acceptsValidSignedJwt()throws Exception{
  var jwt=new SecurityConfig().jwtDecoder(key).decode(signed("https://community-repair.local",List.of("community-api"),Instant.now().plusSeconds(300),key)).block(Duration.ofSeconds(5));
  assertEquals("resident",jwt.getSubject());
 }
 @Test void rejectsWrongSignature()throws Exception{
  String value=signed("https://community-repair.local",List.of("community-api"),Instant.now().plusSeconds(300),"different-unit-test-key-12345678901234567890");
  assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder(key).decode(value).block(Duration.ofSeconds(5)));
 }
 @Test void rejectsExpiredJwt()throws Exception{
  String value=signed("https://community-repair.local",List.of("community-api"),Instant.now().minusSeconds(600),key);
  assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder(key).decode(value).block(Duration.ofSeconds(5)));
 }
 @Test void rejectsWrongIssuer()throws Exception{
  String value=signed("https://other-issuer.local",List.of("community-api"),Instant.now().plusSeconds(300),key);
  assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder(key).decode(value).block(Duration.ofSeconds(5)));
 }
 @Test void rejectsWrongAudience()throws Exception{
  String value=signed("https://community-repair.local",List.of("other-api"),Instant.now().plusSeconds(300),key);
  assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder(key).decode(value).block(Duration.ofSeconds(5)));
 }
 @Test void rejectsMissingExpiration()throws Exception{
  String value=signed("https://community-repair.local",List.of("community-api"),null,key);
  assertThrows(JwtException.class,()->new SecurityConfig().jwtDecoder(key).decode(value).block(Duration.ofSeconds(5)));
 }
 @Test void overwritesSpoofedIdentityAndStripsBearer(){
  var request=MockServerHttpRequest.get("/api/auth/me").header("X-Authenticated-User","admin")
   .header("X-Authenticated-Role","ADMIN").header("X-Internal-API-Key","forged").header("Authorization","Bearer sensitive").build();
  var jwt=Jwt.withTokenValue("unit-test").header("alg","HS256").subject("resident").claim("roles",List.of("RESIDENT")).build();
  var exchange=MockServerWebExchange.from(request).mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
  var result=new AtomicReference<org.springframework.http.HttpHeaders>();
  new TrustedIdentityFilter(key).filter(exchange,e->{result.set(e.getRequest().getHeaders());return Mono.empty();}).block(Duration.ofSeconds(5));
  assertEquals("resident",result.get().getFirst("X-Authenticated-User"));
  assertEquals("RESIDENT",result.get().getFirst("X-Authenticated-Role"));
  assertEquals(key,result.get().getFirst("X-Internal-API-Key"));assertNull(result.get().getFirst("Authorization"));
 }
}
