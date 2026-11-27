package com.example.mpdemo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.mpdemo.mapper")
@EnableScheduling
public class DazuoyeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DazuoyeApplication.class, args);
    }
}
