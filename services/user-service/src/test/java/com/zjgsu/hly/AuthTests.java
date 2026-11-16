package com.zjgsu.hly;
import com.zjgsu.hly.auth.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class AuthTests {
 @Autowired AuthService auth;
 @Autowired LoginAccountRepository accounts;
 @Test void passwordIsSaltedHashAndJwtContainsRole(){
  String password="test-password-only-123";
  String hash=auth.hash(password);
  assertTrue(hash.startsWith("$2"));assertNotEquals(password,hash);assertNotEquals(hash,auth.hash(password));
  accounts.saveAndFlush(new LoginAccount("test-admin",hash,"ADMIN"));
  var result=auth.login("test-admin",password);
  var key=new SecretKeySpec("unit-test-only-signing-key-not-for-deployment-123456".getBytes(StandardCharsets.UTF_8),"HmacSHA256");
  var jwt=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(result.accessToken());
  assertEquals("test-admin",jwt.getSubject());assertEquals("https://community-repair.local",jwt.getIssuer().toString());
  assertTrue(jwt.getClaimAsStringList("roles").contains("ADMIN"));
  assertTrue(jwt.getAudience().contains("community-api"));assertNotNull(jwt.getExpiresAt());
 }
 @Test void wrongPasswordAndMissingAccountReturn401(){
  accounts.saveAndFlush(new LoginAccount("test-resident",auth.hash("test-resident-pass"),"RESIDENT"));
  assertEquals(401,assertThrows(ResponseStatusException.class,()->auth.login("test-resident","wrong-password")).getStatusCode().value());
  assertEquals(401,assertThrows(ResponseStatusException.class,()->auth.login("missing","wrong-password")).getStatusCode().value());
 }
 @Test void doesNotAcceptRoleFromLoginInput(){
  accounts.saveAndFlush(new LoginAccount("resident-only",auth.hash("test-resident-pass"),"RESIDENT"));
  assertEquals("RESIDENT",auth.login("resident-only","test-resident-pass").role());
 }
 @Test void rejectsMissingCredentials(){
  assertEquals(401,assertThrows(ResponseStatusException.class,()->auth.login(null,null)).getStatusCode().value());
 }
}
