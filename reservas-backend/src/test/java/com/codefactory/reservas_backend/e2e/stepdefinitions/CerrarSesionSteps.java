package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Credenciales;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.CerrarSesion;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarSusDatos;
import com.codefactory.reservas_backend.e2e.tasks.IniciarSesion;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarCuenta;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;

import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.equalTo;

/** HU-04 - Cerrar sesión. "se le deniega el acceso" está en ComunesSteps. */
public class CerrarSesionSteps {

    // ---------- Given ----------

    @Given("un usuario tiene una sesión activa y autenticada")
    public void usuarioConSesionActiva() {
        iniciarSesionComoCliente();
    }

    @Given("un usuario cerró su sesión exitosamente")
    public void usuarioCerroSuSesion() {
        Actor usuario = iniciarSesionComoCliente();
        usuario.attemptsTo(CerrarSesion.ahora());
        usuario.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
    }

    @Given("un usuario no ha iniciado sesión")
    public void usuarioSinSesion() {
        theActorCalled(USUARIO).remember(Memoria.TOKEN, "");
    }

    // ---------- When ----------

    @When("ejecuta la acción de cerrar sesión")
    public void ejecutaLaAccionDeCerrarSesion() {
        theActorCalled(USUARIO).attemptsTo(CerrarSesion.ahora());
    }

    @When("intenta cerrar sesión")
    public void intentaCerrarSesion() {
        theActorCalled(USUARIO).attemptsTo(CerrarSesion.ahora());
    }

    @When("intenta realizar una operación protegida con esa sesión")
    public void intentaUnaOperacionProtegida() {
        theActorCalled(USUARIO).attemptsTo(ConsultarSusDatos.conSuSesion());
    }

    // ---------- Then / And ----------

    @Then("el cierre de sesión es exitoso")
    public void elCierreEsExitoso() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
    }

    /** Un 401 (y no un 403) significa que la plataforma le pide autenticarse otra vez. */
    @And("debe iniciar sesión nuevamente")
    public void debeIniciarSesionNuevamente() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(401)));
    }

    // ---------- Apoyo ----------

    /** Registra un cliente, inicia su sesión y deja recordados su id y su sesión. */
    private Actor iniciarSesionComoCliente() {
        Actor usuario = theActorCalled(USUARIO);
        DatosDeRegistro datos = DatosDeRegistro.validos();
        usuario.attemptsTo(RegistrarCuenta.comoCliente(datos));
        usuario.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        usuario.remember(Memoria.ID_DE_USUARIO, RespuestaDeLaApi.idDelUsuario().answeredBy(usuario));
        usuario.attemptsTo(IniciarSesion.con(new Credenciales(datos.correo(), datos.contrasena(), null)));
        usuario.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        return usuario;
    }
}
