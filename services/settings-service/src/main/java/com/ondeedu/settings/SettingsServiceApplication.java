package com.ondeedu.settings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.ondeedu.settings", "com.ondeedu.common"})
@EnableDiscoveryClient
public class SettingsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettingsServiceApplication.class, args);
    }
}
