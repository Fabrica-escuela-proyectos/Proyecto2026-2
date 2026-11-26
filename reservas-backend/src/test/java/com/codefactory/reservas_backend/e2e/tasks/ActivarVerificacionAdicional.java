package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.support.TotpTestSupport;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

/**
 * Completa el enrolamiento de la verificación adicional (MFA) del Administrador, como lo haría con su app
 * autenticadora: pide el secreto, calcula el código vigente y lo confirma. Deja el secreto en la memoria del actor.
 */
public final class ActivarVerificacionAdicional {

    private ActivarVerificacionAdicional() {
    }

    public static Performable conSuAplicacion() {
        return Task.where("{0} activa su verificación adicional", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/auth/mfa/setup").with(request -> ConSesion.deAcceso(token).apply(request)));
            String uri = LastResponse.received().answeredBy(actor).jsonPath().getString("otpauthUri");
            String secreto = TotpTestSupport.secretFromOtpauthUri(uri);
            actor.remember(Memoria.SECRETO_MFA, secreto);
            actor.attemptsTo(Post.to("/api/v1/auth/mfa/activate").with(request -> ConSesion.deAcceso(token)
                    .apply(request).body("{\"code\":\"" + TotpTestSupport.codeNow(secreto) + "\"}")));
        });
    }
}
