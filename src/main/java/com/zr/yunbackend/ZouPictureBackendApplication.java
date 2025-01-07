package com.zr.yunbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.zr.yunbackend.mapper")
@EnableScheduling // 确保启用了调度功能
public class ZouPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZouPictureBackendApplication.class, args);
    }
}
