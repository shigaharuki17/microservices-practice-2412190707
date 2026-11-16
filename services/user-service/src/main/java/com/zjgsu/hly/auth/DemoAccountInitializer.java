package com.zjgsu.hly.auth;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
@Component
public class DemoAccountInitializer implements CommandLineRunner {
    private final LoginAccountRepository accounts; private final AuthService auth;
    @Value("${DEMO_ADMIN_PASSWORD:}") private String adminPassword;
    @Value("${DEMO_RESIDENT_PASSWORD:}") private String residentPassword;
    public DemoAccountInitializer(LoginAccountRepository accounts,AuthService auth){this.accounts=accounts;this.auth=auth;}
    public void run(String...args){seed("admin","ADMIN",adminPassword);seed("resident","RESIDENT",residentPassword);}
    private void seed(String username,String role,String password){
        if(password.isBlank() || accounts.findByUsername(username).isPresent())return;
        if(password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Demo passwords require 12 to 72 UTF-8 bytes");
        try {accounts.saveAndFlush(new LoginAccount(username,auth.hash(password),role));}
        catch(DataIntegrityViolationException concurrentInstanceAlreadyCreatedAccount) {
            if(accounts.findByUsername(username).isEmpty())throw concurrentInstanceAlreadyCreatedAccount;
        }
    }
}
