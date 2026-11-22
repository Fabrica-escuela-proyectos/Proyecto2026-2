package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Roles;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarCuenta;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

import static com.codefactory.reservas_backend.e2e.user.Personajes.CLIENTE_EXISTENTE;
import static com.codefactory.reservas_backend.e2e.user.Personajes.VISITANTE;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;

/** HU-01 - Registro de cliente. */
public class RegistroClienteSteps {

    @Autowired
    private UserRepository userRepository;

    // ---------- Given ----------

    @Given("an unregistered visitor wants to create a client account")
    public void visitanteQuiereCrearCuentaDeCliente() {
        theActorCalled(VISITANTE).remember(Memoria.DATOS, DatosDeRegistro.validos());
    }

    @Given("a visitor has a valid name, email and password")
    public void visitanteConNombreCorreoYContrasenaValidos() {
        visitanteQuiereCrearCuentaDeCliente();
    }

    @Given("a visitor has a valid name, email and cell phone number")
    public void visitanteConNombreCorreoYCelularValidos() {
        visitanteQuiereCrearCuentaDeCliente();
    }

    @Given("a client account is already registered")
    public void yaExisteUnaCuentaDeCliente() {
        Actor existente = theActorCalled(CLIENTE_EXISTENTE);
        existente.attemptsTo(RegistrarCuenta.comoCliente(DatosDeRegistro.validos()));
        existente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        visitanteQuiereCrearCuentaDeCliente();
    }

    // ---------- When ----------

    @When("they complete all the data with valid information and submit the request")
    public void completaTodosLosDatosYEnvia() {
        enviar(datosDelVisitante());
    }

    @When("they enter the email {string} and submit the request")
    public void ingresaElCorreoYEnvia(String correo) {
        enviar(datosDelVisitante().conCorreo(correo));
    }

    @When("they submit the request with the cell phone number {string}")
    public void enviaConElCelular(String celular) {
        enviar(datosDelVisitante().conCelular(celular));
    }

    @When("they submit the password {string} that includes the special character {string}")
    public void enviaContrasenaConCaracterEspecial(String contrasena, String caracterEspecial) {
        enviar(datosDelVisitante().conContrasena(contrasena));
    }

    @When("they submit the password {string} that fails the requirement {string}")
    public void enviaContrasenaQueIncumple(String contrasena, String requisito) {
        enviar(datosDelVisitante().conContrasena(contrasena));
    }

    @When("they leave the field {string} empty and submit the request")
    public void dejaVacioElCampoYEnvia(String campo) {
        enviar(datosDelVisitante().sinElCampo(campo));
    }

    @When("a visitor tries to register with the same email as that account")
    public void intentaRegistrarseConElMismoCorreo() {
        DatosDeRegistro existente = theActorCalled(CLIENTE_EXISTENTE).recall(Memoria.DATOS);
        enviar(DatosDeRegistro.validos().conCorreo(existente.correo()));
    }

    @When("a visitor tries to register with the same cell phone number as that account")
    public void intentaRegistrarseConElMismoCelular() {
        DatosDeRegistro existente = theActorCalled(CLIENTE_EXISTENTE).recall(Memoria.DATOS);
        enviar(DatosDeRegistro.validos().conCelular(existente.celular()));
    }

    // ---------- Then / And ----------

    @Then("the account is created successfully")
    public void laCuentaQuedaCreada() {
        Actor visitante = theActorCalled(VISITANTE);
        visitante.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        assertThat(userRepository.findByEmailIgnoreCase(datosDelVisitante().correo())).isPresent();
    }

    @And("it has the role {string} by default")
    public void tieneElRolPorDefecto(String rol) {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.rol(), equalTo(Roles.nombreEnLaApi(rol))));
    }

    @Then("the request is rejected and no duplicate account is created")
    public void rechazadaPorDuplicado() {
        theActorCalled(VISITANTE).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)));
    }

    @Then("the request is rejected and no account is created")
    public void rechazadaYSinCuenta() {
        Actor visitante = theActorCalled(VISITANTE);
        visitante.should(seeThat(RespuestaDeLaApi.codigoDeEstado(),
                allOf(greaterThanOrEqualTo(400), lessThan(500))));
        assertThat(userRepository.findByEmailIgnoreCase(datosDelVisitante().correo())).isEmpty();
    }

    @And("they receive a message saying the email is already in use")
    public void mensajeCorreoEnUso() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensaje(), containsString("correo electrónico ya está en uso")));
    }

    @And("they receive a message saying the cell phone number is already in use")
    public void mensajeCelularEnUso() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensaje(), containsString("celular ya está en uso")));
    }

    @And("they receive an error message saying the email format is not valid")
    public void mensajeFormatoDeCorreo() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensajeDelCampo("email"), containsString("no es válido")));
    }

    @And("they receive an error message saying the cell phone number is not valid")
    public void mensajeCelularInvalido() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensajeDelCampo("cellphone"), containsString("no es válido")));
    }

    @And("they receive a message stating the security requirement that was not met")
    public void mensajePoliticaDeContrasena() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensajeDelCampo("password"), containsString("política de seguridad")));
    }

    @And("they receive a specific error message for the field {string}")
    public void mensajeEspecificoParaElCampo(String campo) {
        theActorCalled(VISITANTE).should(seeThat(
                RespuestaDeLaApi.mensajeDelCampo(DatosDeRegistro.claveEnLaRespuesta(campo)),
                not(emptyOrNullString())));
    }

    // ---------- Apoyo ----------

    private DatosDeRegistro datosDelVisitante() {
        return theActorCalled(VISITANTE).recall(Memoria.DATOS);
    }

    private void enviar(DatosDeRegistro datos) {
        theActorCalled(VISITANTE).attemptsTo(RegistrarCuenta.conDatos(datos));
    }
}
