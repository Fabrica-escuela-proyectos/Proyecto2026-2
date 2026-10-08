package com.codefactory.reservas_backend.identity.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regresión del issue #8 (el 6.º intento de registro creaba la cuenta) y
 * pruebas del control de fuerza bruta de login/MFA (OWASP A07). El tiempo se
 * controla con un reloj manual para que las ventanas y los bloqueos sean
 * deterministas.
 */
class AttemptLimiterTest {

    private static final String ORIGEN = "10.0.0.1";

    private MutableClock clock;
    private AttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-07T12:00:00Z"));
        // 5 intentos, ventana de 10 minutos, bloqueo de 15 (valores de registro).
        limiter = new AttemptLimiter(5, Duration.ofMinutes(10), Duration.ofMinutes(15), clock);
    }

    // --- tryAcquire: cada intento cuenta (registro) ---

    @Test
    void debePermitirExactamenteCincoIntentosYRechazarElSexto() {
        // Issue #8: antes el 6.º intento pasaba y recién el 7.º se rechazaba.
        for (int intento = 1; intento <= 5; intento++) {
            assertThat(limiter.tryAcquire(ORIGEN)).as("intento %d", intento).isTrue();
        }

        assertThat(limiter.tryAcquire(ORIGEN)).as("intento 6").isFalse();
    }

    @Test
    void elSextoIntentoFijaElBloqueoYLosSiguientesSiguenRechazados() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(ORIGEN);
        }

        assertThat(limiter.tryAcquire(ORIGEN)).isFalse();
        assertThat(limiter.isBlocked(ORIGEN)).isTrue();
        assertThat(limiter.tryAcquire(ORIGEN)).isFalse();
        assertThat(limiter.tryAcquire(ORIGEN)).isFalse();
    }

    @Test
    void elBloqueoTerminaALosQuinceMinutosYVuelveAHaberCincoCupos() {
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire(ORIGEN);
        }

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(limiter.tryAcquire(ORIGEN)).as("aún bloqueado").isFalse();

        clock.advance(Duration.ofSeconds(2));
        assertThat(limiter.isBlocked(ORIGEN)).isFalse();
        for (int intento = 1; intento <= 5; intento++) {
            assertThat(limiter.tryAcquire(ORIGEN)).as("tras el bloqueo, intento %d", intento).isTrue();
        }
        assertThat(limiter.tryAcquire(ORIGEN)).isFalse();
    }

    @Test
    void laVentanaSeReiniciaDespuesDeDiezMinutosSinLlegarAlMaximo() {
        for (int i = 0; i < 4; i++) {
            assertThat(limiter.tryAcquire(ORIGEN)).isTrue();
        }

        clock.advance(Duration.ofMinutes(10));

        for (int intento = 1; intento <= 5; intento++) {
            assertThat(limiter.tryAcquire(ORIGEN)).as("ventana nueva, intento %d", intento).isTrue();
        }
        assertThat(limiter.tryAcquire(ORIGEN)).isFalse();
    }

    @Test
    void cadaOrigenTieneSuPropioContador() {
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire(ORIGEN);
        }

        assertThat(limiter.isBlocked(ORIGEN)).isTrue();
        assertThat(limiter.isBlocked("10.0.0.2")).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    @Test
    void conVeinteHilosSimultaneosSoloPasaElCupoDisponible() throws Exception {
        // La causa del issue #8 era no atómica: consultar y contar en dos
        // pasos dejaba pasar a más de las solicitudes permitidas.
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return limiter.tryAcquire(ORIGEN);
            }));
        }
        salida.countDown();

        long permitidos = 0;
        for (Future<Boolean> resultado : resultados) {
            if (resultado.get(10, TimeUnit.SECONDS)) {
                permitidos++;
            }
        }
        pool.shutdownNow();

        assertThat(permitidos).isEqualTo(5);
    }

    // --- recordFailure / isBlocked / reset: solo cuentan los fallos (login) ---

    @Test
    void elQuintoFalloBloqueaLaClave() {
        for (int fallo = 1; fallo <= 4; fallo++) {
            limiter.recordFailure(ORIGEN);
            assertThat(limiter.isBlocked(ORIGEN)).as("tras %d fallos", fallo).isFalse();
        }

        limiter.recordFailure(ORIGEN);

        assertThat(limiter.isBlocked(ORIGEN)).isTrue();
    }

    @Test
    void consultarElBloqueoNoCuentaIntentos() {
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.isBlocked(ORIGEN)).isFalse();
        }

        assertThat(limiter.tryAcquire(ORIGEN)).isTrue();
    }

    @Test
    void unExitoBorraElHistorialDeFallos() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(ORIGEN);
        }

        limiter.reset(ORIGEN);
        limiter.recordFailure(ORIGEN);

        assertThat(limiter.isBlocked(ORIGEN)).isFalse();
    }

    @Test
    void losFallosFueraDeLaVentanaNoSeAcumulan() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(ORIGEN);
        }

        clock.advance(Duration.ofMinutes(10));
        limiter.recordFailure(ORIGEN);

        assertThat(limiter.isBlocked(ORIGEN)).isFalse();
    }

    @Test
    void unFalloDuranteElBloqueoNoLoExtiende() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(ORIGEN);
        }
        clock.advance(Duration.ofMinutes(10));
        limiter.recordFailure(ORIGEN);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(limiter.isBlocked(ORIGEN)).isFalse();
    }

    // --- Validación y limpieza ---

    @Test
    void debeRechazarUnMaximoDeIntentosInvalido() {
        assertThatThrownBy(() -> new AttemptLimiter(0, Duration.ofMinutes(1), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debePurgarLasClavesVencidasCuandoHayDemasiadasSeguidas() {
        for (int i = 0; i < 10_001; i++) {
            limiter.tryAcquire("origen-" + i);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(10_001);

        clock.advance(Duration.ofMinutes(11));
        limiter.tryAcquire("origen-nuevo");

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    /** Reloj que solo avanza cuando la prueba lo indica. */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
