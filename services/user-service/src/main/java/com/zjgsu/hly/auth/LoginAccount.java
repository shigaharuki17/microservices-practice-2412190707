package com.zjgsu.hly.auth;
import jakarta.persistence.*;
@Entity @Table(name="login_accounts")
public class LoginAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,unique=true,length=80) public String username;
    @Column(nullable=false,length=100) public String passwordHash;
    @Column(nullable=false,length=20) public String role;
    protected LoginAccount() {}
    public LoginAccount(String username,String passwordHash,String role) {
        this.username=username;this.passwordHash=passwordHash;this.role=role;
    }
}
