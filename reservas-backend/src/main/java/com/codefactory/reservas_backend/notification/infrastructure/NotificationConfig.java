package com.codefactory.reservas_backend.notification.infrastructure;

import com.codefactory.reservas_backend.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

/**
 * Configuración del correo (ADR-009): Brevo si hay clave y remitente; si no, solo log. Los avisos se envían en un
 * grupo de hilos propio y pequeño para no competir con las peticiones HTTP.
 */
@Configuration
@EnableAsync
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    public EmailSender emailSender(ObjectMapper objectMapper,
                                   @Value("${notifications.brevo.api-key:}") String apiKey,
                                   @Value("${notifications.sender.email:}") String senderEmail,
                                   @Value("${notifications.sender.name:Plataforma de Reservas}") String senderName) {
        if (apiKey.isBlank()) {
            log.info("Correo desactivado: sin BREVO_API_KEY los avisos solo se escriben en el log");
            return new LogEmailSender();
        }
        if (senderEmail.isBlank()) {
            log.warn("Correo desactivado: hay BREVO_API_KEY pero falta NOTIFICATION_SENDER_EMAIL (remitente verificado en Brevo)");
            return new LogEmailSender();
        }
        log.info("Correo activo: avisos por la API de Brevo");
        return new BrevoEmailSender(objectMapper, apiKey.trim(), senderEmail.trim(), senderName);
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("aviso-");
        executor.initialize();
        return executor;
    }
}
