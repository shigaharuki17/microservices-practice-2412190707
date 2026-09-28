package com.zjgsu.hly;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello() {
        return Map.of("project", "社区报修与上门维修服务平台", "message", "欢迎使用社区报修平台！");
    }
}
