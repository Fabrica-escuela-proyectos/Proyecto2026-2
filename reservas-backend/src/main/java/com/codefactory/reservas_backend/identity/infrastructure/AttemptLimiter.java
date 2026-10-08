package com.codefactory.reservas_backend.identity.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de intentos en memoria, por clave (IP, IP+correo, id de usuario...),
 * con ventana fija y bloqueo temporal. Es la pieza reutilizable detrás del
 * límite de registro (HU-01/HU-03, ver {@link RegistrationRateLimiter}) y del
 * control de fuerza bruta de login, MFA y operaciones sensibles (OWASP A07,
 * ver {@link AuthAttemptLimiter}).
 *
 * Cada clave tiene un único estado inmutable que se reemplaza dentro de
 * {@code ConcurrentHashMap.compute}, así que "contar y decidir" es atómico:
 * dos solicitudes simultáneas nunca pueden pasar las dos con el último cupo
 * (el defecto del limitador original, issue #8, que consultaba y contaba en
 * dos pasos separados).
 *
 * Hay dos formas de uso, que no se mezclan sobre la misma instancia:
 * <ul>
 *   <li>{@link #tryAcquire}: cada intento cuenta (registro). Los primeros
 *       {@code maxAttempts} se permiten; el siguiente se rechaza y fija el
 *       bloqueo.</li>
 *   <li>{@link #isBlocked} + {@link #recordFailure} + {@link #reset}: solo
 *       cuentan los fallos (login). Al llegar a {@code maxAttempts} fallos se
 *       fija el bloqueo; un éxito borra el historial.</li>
 * </ul>
 *
 * Suficiente para una sola instancia del backend. Con varias instancias el
 * estado debería vivir en un almacén compartido (p. ej. Redis); ver
 * docs/ADR-002-insumos.md.
 */
public class AttemptLimiter {

    /** Tope de claves seguidas antes de purgar las vencidas (evita crecer sin límite). */
    private static final int PURGE_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxAttempts, Duration window, Duration blockDuration) {
        this(maxAttempts, window, blockDuration, Clock.systemUTC());
    }

    AttemptLimiter(int maxAttempts, Duration window, Duration blockDuration, Clock clock) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser al menos 1");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    /**
     * Cuenta un intento si la clave no está bloqueada. Devuelve {@code true}
     * para los primeros {@code maxAttempts} dentro de la ventana; el
     * siguiente devuelve {@code false} y bloquea la clave por
     * {@code blockDuration}.
     */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        boolean[] allowed = {false};
        states.compute(key, (k, current) -> {
            State state = refreshed(current, now);
            if (state.blockedAt(now)) {
                return state;
            }
            if (state.count() >= maxAttempts) {
                return new State(state.count(), state.windowStart(), now.plus(blockDuration));
            }
            allowed[0] = true;
            return new State(state.count() + 1, state.windowStart(), null);
        });
        purgeIfNeeded(now);
        return allowed[0];
    }

    /** {@code true} si la clave está bloqueada ahora mismo. No cuenta ningún intento. */
    public boolean isBlocked(String key) {
        State state = states.get(key);
        return state != null && state.blockedAt(clock.instant());
    }

    /**
     * Registra un fallo. Al llegar a {@code maxAttempts} fallos dentro de la
     * ventana, la clave queda bloqueada por {@code blockDuration}.
     */
    public void recordFailure(String key) {
        Instant now = clock.instant();
        states.compute(key, (k, current) -> {
            State state = refreshed(current, now);
            if (state.blockedAt(now)) {
                return state;
            }
            int failures = state.count() + 1;
            Instant blockedUntil = failures >= maxAttempts ? now.plus(blockDuration) : null;
            return new State(failures, state.windowStart(), blockedUntil);
        });
        purgeIfNeeded(now);
    }

    /** Olvida el historial de la clave (p. ej. tras un inicio de sesión exitoso). */
    public void reset(String key) {
        states.remove(key);
    }

    /** Estado vigente: si la ventana o el bloqueo ya vencieron, empieza uno nuevo. */
    private State refreshed(State current, Instant now) {
        if (current == null) {
            return State.fresh(now);
        }
        if (current.blockedUntil() != null) {
            return now.isBefore(current.blockedUntil()) ? current : State.fresh(now);
        }
        return now.isBefore(current.windowStart().plus(window)) ? current : State.fresh(now);
    }

    /** Claves que se están siguiendo ahora mismo (visible para las pruebas de la purga). */
    int trackedKeys() {
        return states.size();
    }

    private void purgeIfNeeded(Instant now) {
        if (states.size() > PURGE_THRESHOLD) {
            states.entrySet().removeIf(entry -> entry.getValue().expiredAt(now, window));
        }
    }

    private record State(int count, Instant windowStart, Instant blockedUntil) {

        static State fresh(Instant now) {
            return new State(0, now, null);
        }

        boolean blockedAt(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        boolean expiredAt(Instant now, Duration window) {
            return blockedUntil != null
                    ? !now.isBefore(blockedUntil)
                    : !now.isBefore(windowStart.plus(window));
        }
    }
}
