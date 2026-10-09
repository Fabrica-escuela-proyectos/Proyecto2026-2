package com.codefactory.reservas_backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj de negocio: hora local de Colombia. Los horarios de los recursos (HU-19),
 * la antelación mínima (HU-08) y las fechas consultables (HU-20) se interpretan en
 * esta zona. Es un bean para poder fijarlo en las pruebas.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    @Bean
    public Clock businessClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
