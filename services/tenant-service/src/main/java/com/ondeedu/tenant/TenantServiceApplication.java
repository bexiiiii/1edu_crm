package com.ondeedu.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.ondeedu.tenant", "com.ondeedu.common"})
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling
public class TenantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
