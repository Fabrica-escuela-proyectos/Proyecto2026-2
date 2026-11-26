package com.codefactory.reservas_backend.e2e.user;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.models.Horario;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.AsignarRecursos;
import com.codefactory.reservas_backend.e2e.tasks.CrearServicio;
import com.codefactory.reservas_backend.e2e.tasks.DefinirHorario;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarRecurso;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.restassured.path.json.JsonPath;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Preparación de las HU de reservas: un negocio que puede recibir reservas y las reservas mismas.
 * - Por la API: registrar al proveedor, crear servicio y recurso, asignarlo, darle horario, y que un cliente reserve.
 * - Directo en la base (BookingRepository): las reservas que la API no deja crear, como una en el pasado o una que
 *   empieza en menos de 1 hora (la antelación mínima del negocio es 1 hora). Igual que CuentasDePrueba crea al
 *   administrador directo en la base.
 */
public final class AgendaDePrueba {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    public static final long PRECIO_POR_DEFECTO = 30_000;
    public static final int DURACION = 60;
    private static final String ABRE = "06:00";
    private static final String CIERRA = "22:00";

    // Horas de inicio de las reservas que se crean por la API (08:00 a 19:00); se rotan para no repetir en un escenario.
    private static final AtomicInteger HORAS = new AtomicInteger();

    private AgendaDePrueba() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(BOGOTA);
    }

    /** Siguiente horario libre a "dias" días de hoy, en punto, entre las 08:00 y las 19:00. */
    public static Horario horarioEn(int dias) {
        return new Horario(hoy().plusDays(dias), LocalTime.of(8 + HORAS.getAndIncrement() % 12, 0));
    }

    /** Registra al proveedor por la API, inicia su sesión y deja su negocio listo para recibir reservas. */
    public static Agenda prepararNegocio(Actor proveedor) {
        return prepararNegocio(proveedor, PRECIO_POR_DEFECTO);
    }

    public static Agenda prepararNegocio(Actor proveedor, long precio) {
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        return montarAgenda(proveedor, precio, "Servicio " + UUID.randomUUID().toString().substring(0, 6),
                "Sala " + UUID.randomUUID().toString().substring(0, 6), ABRE, CIERRA);
    }

    // el proveedor ya tiene sesion; crea servicio y recurso con los nombres y el horario pedidos
    public static Agenda montarAgenda(Actor proveedor, long precio, String nombreDelServicio, String nombreDelRecurso,
                                      String abre, String cierra) {
        String idDelNegocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        DatosDeRegistro datos = proveedor.recall(Memoria.DATOS);

        proveedor.attemptsTo(CrearServicio.enElNegocio(idDelNegocio, nombreDelServicio, DURACION, precio));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String idDelServicio = ultimaRespuesta(proveedor).getString("id");

        proveedor.attemptsTo(RegistrarRecurso.llamado(nombreDelRecurso, idDelNegocio));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String idDelRecurso = ultimaRespuesta(proveedor).getString("id");

        proveedor.attemptsTo(AsignarRecursos.alServicio(idDelServicio, idDelRecurso));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        proveedor.attemptsTo(DefinirHorario.todaLaSemana(idDelRecurso, abre, cierra));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));

        return new Agenda(idDelNegocio, datos.nombreDelNegocio(), idDelServicio, nombreDelServicio,
                idDelRecurso, nombreDelRecurso, DURACION, precio);
    }

    /** El cliente (ya con sesión) reserva por la API; devuelve el id de la reserva. */
    public static String reservar(Actor cliente, Agenda agenda, Horario horario) {
        cliente.attemptsTo(ReservarServicio.en(agenda.idDelServicio(), horario, agenda.duracionEnMinutos()));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        return ultimaRespuesta(cliente).getString("id");
    }

    /**
     * Guarda una reserva directamente en la base, con el estado y el inicio indicados y el precio de la agenda.
     * El cliente debe estar registrado (se usan su id, nombre y correo).
     */
    public static String insertarReserva(BookingRepository reservas, Agenda agenda, Actor cliente, Instant inicio,
                                         BookingStatus estado, long precio) {
        DatosDeRegistro datos = cliente.recall(Memoria.DATOS);
        String idDelCliente = cliente.recall(Memoria.ID_DE_USUARIO);
        Booking reserva = Booking.builder()
                .clientId(UUID.fromString(idDelCliente))
                .clientName(datos.nombreCompleto())
                .clientEmail(datos.correo())
                .businessId(UUID.fromString(agenda.idDelNegocio()))
                .businessName(agenda.nombreDelNegocio())
                .serviceId(UUID.fromString(agenda.idDelServicio()))
                .serviceName(agenda.nombreDelServicio())
                .resourceId(UUID.fromString(agenda.idDelRecurso()))
                .resourceName(agenda.nombreDelRecurso())
                .startAt(inicio)
                .endAt(inicio.plus(Duration.ofMinutes(agenda.duracionEnMinutos())))
                .status(estado)
                .priceCop(precio)
                .build();
        if (estado == BookingStatus.CANCELADA) {
            reserva.setCancelOrigin(CancelOrigin.CLIENTE);
            reserva.setCancelledAt(Instant.now());
        }
        return reservas.save(reserva).getId().toString();
    }

    public static String insertarReserva(BookingRepository reservas, Agenda agenda, Actor cliente, Instant inicio,
                                         BookingStatus estado) {
        return insertarReserva(reservas, agenda, cliente, inicio, estado, agenda.precio());
    }

    /** Inicio de una reserva en una fecha y hora de Bogotá. */
    public static Instant a(LocalDate fecha, int hora) {
        return fecha.atTime(hora, 0).atZone(BOGOTA).toInstant();
    }

    private static JsonPath ultimaRespuesta(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath();
    }
}
