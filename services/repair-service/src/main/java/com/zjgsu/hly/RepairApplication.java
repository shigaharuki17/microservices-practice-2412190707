package com.zjgsu.hly;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.cloud.openfeign.EnableFeignClients
@SpringBootApplication
public class RepairApplication {
    public static void main(String[] args) { SpringApplication.run(RepairApplication.class, args); }
}
