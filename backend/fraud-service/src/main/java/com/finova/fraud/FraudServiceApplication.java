package com.finova.fraud;

import com.finova.common.security.JwtProperties;
import com.finova.fraud.config.FraudProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties({JwtProperties.class, FraudProperties.class})
@EnableScheduling
public class FraudServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudServiceApplication.class, args);
    }
}