package com.trustai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TrustaiBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustaiBackendApplication.class, args);
    }
     }