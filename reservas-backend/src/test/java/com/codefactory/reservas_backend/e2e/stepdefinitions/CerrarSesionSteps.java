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

    @Given("a user has an active, authenticated session")
    public void usuarioConSesionActiva() {
        iniciarSesionComoCliente();
    }

    @Given("a user logged out successfully")
    public void usuarioCerroSuSesion() {
        Actor usuario = iniciarSesionComoCliente();
        usuario.attemptsTo(CerrarSesion.ahora());
        usuario.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
    }

    @Given("a user has not logged in")
    public void usuarioSinSesion() {
        theActorCalled(USUARIO).remember(Memoria.TOKEN, "");
    }

    // ---------- When ----------

    @When("they perform the log out action")
    public void ejecutaLaAccionDeCerrarSesion() {
        theActorCalled(USUARIO).attemptsTo(CerrarSesion.ahora());
    }

    @When("they try to log out")
    public void intentaCerrarSesion() {
        theActorCalled(USUARIO).attemptsTo(CerrarSesion.ahora());
    }

    @When("they try to perform a protected operation with that session")
    public void intentaUnaOperacionProtegida() {
        theActorCalled(USUARIO).attemptsTo(ConsultarSusDatos.conSuSesion());
    }

    // ---------- Then / And ----------

    @Then("the logout is successful")
    public void elCierreEsExitoso() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
    }

    /** Un 401 (y no un 403) significa que la plataforma le pide autenticarse otra vez. */
    @And("they must log in again")
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
