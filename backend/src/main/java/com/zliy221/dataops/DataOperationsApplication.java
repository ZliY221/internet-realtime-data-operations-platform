package com.zliy221.dataops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DataOperationsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataOperationsApplication.class, args);
    }
}

