package com.bank.accountidentityservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {

    private int otpExpiryMinutes;

    private String bankName;

    private String fromEmail;
}
