package com.codefactory.reservas_backend.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultasSprint2DocumentationTest {

    private static final Pattern UUID_LITERAL_CAST =
            Pattern.compile("'[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'\\s*::\\s*uuid");

    @Test
    void lasConsultasDeAplicacionNoDebenUsarUuidHardcodeados() throws IOException {
        String sql = Files.readString(resolveConsultasPath());

        assertThat(UUID_LITERAL_CAST.matcher(sql).find())
                .as("consultas_sprint2.sql no debe incluir literales UUID casteados")
                .isFalse();
    }

    @Test
    void lasConsultasDeAplicacionDebenMantenerParametrosNombrados() throws IOException {
        String sql = Files.readString(resolveConsultasPath());

        assertThat(sql)
                .contains(":businessId")
                .contains(":serviceId")
                .contains(":resourceId")
                .contains(":clientId")
                .contains(":bookingId")
                .contains("datos_ejemplo_sprint2.sql");
    }

    private static Path resolveConsultasPath() {
        Path fromModule = Path.of("..", "docs", "bd", "consultas", "consultas_sprint2.sql");
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return Path.of("docs", "bd", "consultas", "consultas_sprint2.sql");
    }
}
