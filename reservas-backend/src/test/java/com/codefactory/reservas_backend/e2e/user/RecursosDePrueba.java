package com.codefactory.reservas_backend.e2e.user;

import com.codefactory.reservas_backend.e2e.models.DatosDeRecurso;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeRecursos;
import com.codefactory.reservas_backend.e2e.tasks.DesactivarRecurso;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarRecursoConDatos;
import net.serenitybdd.screenplay.Actor;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

/** Preparación de recursos por la API para las HU-15 a HU-17. El proveedor ya debe tener sesión y negocio. */
public final class RecursosDePrueba {

    private RecursosDePrueba() {
    }

    /** Registra un recurso en el negocio del proveedor y devuelve su id. */
    public static String crear(Actor proveedor, String nombre, String tipo) {
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        proveedor.attemptsTo(RegistrarRecursoConDatos.en(negocio, DatosDeRecurso.de(nombre, tipo)));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        return RespuestaDeRecursos.id().answeredBy(proveedor);
    }

    /** Desactiva un recurso que no tiene reservas futuras (no hace falta confirmar). */
    public static void desactivar(Actor proveedor, String idDelRecurso) {
        proveedor.attemptsTo(DesactivarRecurso.conId(idDelRecurso, null));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }
}
