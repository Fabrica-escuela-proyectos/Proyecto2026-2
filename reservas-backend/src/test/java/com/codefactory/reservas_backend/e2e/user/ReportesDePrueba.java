package com.codefactory.reservas_backend.e2e.user;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.CrearServicio;
import com.codefactory.reservas_backend.e2e.tasks.DefinirHorario;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarRecurso;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

public final class ReportesDePrueba {

    // el resumen de la plataforma suma todos los negocios: cada escenario usa un dia propio y lejano
    private static final AtomicInteger DIAS_AISLADOS = new AtomicInteger();

    private ReportesDePrueba() {
    }

    public static LocalDate diaAislado() {
        return LocalDate.of(2001, 1, 1).plusDays(DIAS_AISLADOS.getAndIncrement());
    }

    // sin horario cuando abre es null
    public static Agenda nuevoRecurso(Actor proveedor, Agenda agenda, String nombre, String abre, String cierra) {
        proveedor.attemptsTo(RegistrarRecurso.llamado(nombre, agenda.idDelNegocio()));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String id = idDeLaUltimaRespuesta(proveedor);
        if (abre != null) {
            proveedor.attemptsTo(DefinirHorario.todaLaSemana(id, abre, cierra));
            proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        }
        return agenda.conRecurso(id, nombre);
    }

    public static Agenda nuevoServicio(Actor proveedor, Agenda agenda, String nombre) {
        proveedor.attemptsTo(CrearServicio.enElNegocio(agenda.idDelNegocio(), nombre, agenda.duracionEnMinutos(),
                agenda.precio()));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        return agenda.conServicio(idDeLaUltimaRespuesta(proveedor), nombre);
    }

    private static String idDeLaUltimaRespuesta(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath().getString("id");
    }

    // reservas de una hora seguidas desde la hora "abre" de cada dia, saltando al dia siguiente cuando se llena
    public static void reservasSeguidas(BookingRepository reservas, Agenda agenda, Actor cliente, LocalDate desde,
                                        int abre, int horasPorDia, int cantidad, BookingStatus estado) {
        for (int i = 0; i < cantidad; i++) {
            Instant inicio = AgendaDePrueba.a(desde.plusDays(i / horasPorDia), abre + i % horasPorDia);
            AgendaDePrueba.insertarReserva(reservas, agenda, cliente, inicio, estado);
        }
    }
}
