package com.codefactory.reservas_backend.identity.controller;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Regresión del issue de Calidad #10: dos registros simultáneos con el mismo
 * correo devolvían 500 (la verificación "¿existe?" y el INSERT no son
 * atómicos, y la restricción única de la BD lo detectaba demasiado tarde).
 * Ahora el que pierde la carrera recibe 409 con el mismo mensaje que si la
 * cuenta ya existiera. Se repite varias veces porque el resultado depende del
 * orden en que se crucen los hilos.
 */
class RegistrationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int ROUNDS = 5;

    private record Outcome(int status, String message) {
    }

    private Outcome register(String email, String cellphone) throws Exception {
        String payload = """
                {"fullName":"Usuario Concurrente","email":"%s","cellphone":"%s","password":"%s"}
                """.formatted(email, cellphone, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String message = body.isBlank() ? "" : String.valueOf(objectMapper.readTree(body).path("message").asText());
        return new Outcome(result.getResponse().getStatus(), message);
    }

    /** Lanza los dos registros a la vez y devuelve ambos resultados. */
    private List<Outcome> raceTwoRegistrations(String emailA, String phoneA, String emailB, String phoneB) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        futures.add(pool.submit(() -> {
            start.await();
            return register(emailA, phoneA);
        }));
        futures.add(pool.submit(() -> {
            start.await();
            return register(emailB, phoneB);
        }));
        start.countDown();
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return outcomes;
    }

    @Test
    void dosRegistrosSimultaneosConElMismoCorreoDanUn201YUn409() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            String email = uniqueEmail("carrera.correo");

            List<Outcome> outcomes = raceTwoRegistrations(email, uniquePhone(), email, uniquePhone());

            assertThat(outcomes).as("ronda %d: %s", round, outcomes)
                    .extracting(Outcome::status).containsExactlyInAnyOrder(201, 409);
            assertThat(outcomes).filteredOn(o -> o.status() == 409)
                    .extracting(Outcome::message).containsExactly("El correo electrónico ya está en uso");
            assertThat(userRepository.findByEmailIgnoreCase(email)).as("una sola cuenta").isPresent();
        }
    }

    @Test
    void dosRegistrosSimultaneosConElMismoCelularDanUn201YUn409() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            String phone = uniquePhone();

            List<Outcome> outcomes = raceTwoRegistrations(
                    uniqueEmail("carrera.celular.a"), phone, uniqueEmail("carrera.celular.b"), phone);

            assertThat(outcomes).as("ronda %d: %s", round, outcomes)
                    .extracting(Outcome::status).containsExactlyInAnyOrder(201, 409);
            assertThat(outcomes).filteredOn(o -> o.status() == 409)
                    .extracting(Outcome::message).containsExactly("El número de celular ya está en uso");
        }
    }
}
