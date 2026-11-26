package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.models.Credenciales;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.support.TotpTestSupport;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;

/**
 * Deja al actor como un Administrador con la verificación adicional activa y una sesión iniciada con ella.
 * Recuerda su secreto (SECRETO_MFA) para calcular el código vigente en las operaciones sensibles.
 */
public final class AutenticarseComoAdministrador {

    private AutenticarseComoAdministrador() {
    }

    public static Performable conVerificacion(Credenciales sinCodigo) {
        return Task.where("{0} se autentica como administrador con verificación adicional", actor -> {
            actor.attemptsTo(IniciarSesion.con(sinCodigo), ActivarVerificacionAdicional.conSuAplicacion());
            String secreto = actor.recall(Memoria.SECRETO_MFA);
            actor.attemptsTo(IniciarSesion.con(sinCodigo.conCodigo(TotpTestSupport.codeNow(secreto))));
            actor.remember(Memoria.CREDENCIALES, sinCodigo);
        });
    }
}
