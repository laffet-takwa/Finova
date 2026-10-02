package com.finova.notification.config;

import com.finova.notification.service.NotificationFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the notification factory as a plain bean.
 * <p>
 * The class itself carries no Spring annotation and no injected collaborator, which
 * is what keeps it unit-testable as pure logic; this configuration is the only
 * place the framework learns about it.
 */
@Configuration
public class NotificationFactoryConfig {

    @Bean
    public NotificationFactory notificationFactory() {
        return new NotificationFactory();
    }
}