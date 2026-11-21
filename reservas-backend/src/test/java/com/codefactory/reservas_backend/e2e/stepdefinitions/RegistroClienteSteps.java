package com.codefactory.reservas_backend.e2e.stepdefinitions;

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

    @Given("un visitante no registrado quiere crear una cuenta de cliente")
    public void visitanteQuiereCrearCuentaDeCliente() {
        theActorCalled(VISITANTE).remember(Memoria.DATOS, DatosDeRegistro.validos());
    }

    @Given("un visitante tiene nombre, correo y contraseña válidos")
    public void visitanteConNombreCorreoYContrasenaValidos() {
        visitanteQuiereCrearCuentaDeCliente();
    }

    @Given("un visitante tiene nombre, correo y celular válidos")
    public void visitanteConNombreCorreoYCelularValidos() {
        visitanteQuiereCrearCuentaDeCliente();
    }

    @Given("ya existe una cuenta de cliente registrada")
    public void yaExisteUnaCuentaDeCliente() {
        Actor existente = theActorCalled(CLIENTE_EXISTENTE);
        existente.attemptsTo(RegistrarCuenta.comoCliente(DatosDeRegistro.validos()));
        existente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        visitanteQuiereCrearCuentaDeCliente();
    }

    // ---------- When ----------

    @When("completa todos los datos con información válida y envía la solicitud")
    public void completaTodosLosDatosYEnvia() {
        enviar(datosDelVisitante());
    }

    @When("ingresa el correo {string} y envía la solicitud")
    public void ingresaElCorreoYEnvia(String correo) {
        enviar(datosDelVisitante().conCorreo(correo));
    }

    @When("envía la solicitud con el celular {string}")
    public void enviaConElCelular(String celular) {
        enviar(datosDelVisitante().conCelular(celular));
    }

    @When("envía la contraseña {string} que incluye el carácter especial {string}")
    public void enviaContrasenaConCaracterEspecial(String contrasena, String caracterEspecial) {
        enviar(datosDelVisitante().conContrasena(contrasena));
    }

    @When("envía la contraseña {string} que incumple el requisito {string}")
    public void enviaContrasenaQueIncumple(String contrasena, String requisito) {
        enviar(datosDelVisitante().conContrasena(contrasena));
    }

    @When("deja vacío el campo {string} y envía la solicitud")
    public void dejaVacioElCampoYEnvia(String campo) {
        enviar(datosDelVisitante().sinElCampo(campo));
    }

    @When("un visitante intenta registrarse con el mismo correo de esa cuenta")
    public void intentaRegistrarseConElMismoCorreo() {
        DatosDeRegistro existente = theActorCalled(CLIENTE_EXISTENTE).recall(Memoria.DATOS);
        enviar(DatosDeRegistro.validos().conCorreo(existente.correo()));
    }

    @When("un visitante intenta registrarse con el mismo celular de esa cuenta")
    public void intentaRegistrarseConElMismoCelular() {
        DatosDeRegistro existente = theActorCalled(CLIENTE_EXISTENTE).recall(Memoria.DATOS);
        enviar(DatosDeRegistro.validos().conCelular(existente.celular()));
    }

    // ---------- Then / And ----------

    @Then("la cuenta queda creada exitosamente")
    public void laCuentaQuedaCreada() {
        Actor visitante = theActorCalled(VISITANTE);
        visitante.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        assertThat(userRepository.findByEmailIgnoreCase(datosDelVisitante().correo())).isPresent();
    }

    @And("tiene el rol {string} por defecto")
    public void tieneElRolPorDefecto(String rol) {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.rol(), equalTo(rol.toUpperCase(Locale.ROOT))));
    }

    @Then("la solicitud es rechazada y no se crea una cuenta duplicada")
    public void rechazadaPorDuplicado() {
        theActorCalled(VISITANTE).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)));
    }

    @Then("la solicitud es rechazada y no se crea ninguna cuenta")
    public void rechazadaYSinCuenta() {
        Actor visitante = theActorCalled(VISITANTE);
        visitante.should(seeThat(RespuestaDeLaApi.codigoDeEstado(),
                allOf(greaterThanOrEqualTo(400), lessThan(500))));
        assertThat(userRepository.findByEmailIgnoreCase(datosDelVisitante().correo())).isEmpty();
    }

    @And("recibe un mensaje indicando que el correo ya está en uso")
    public void mensajeCorreoEnUso() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensaje(), containsString("correo electrónico ya está en uso")));
    }

    @And("recibe un mensaje indicando que el número de celular ya está en uso")
    public void mensajeCelularEnUso() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensaje(), containsString("celular ya está en uso")));
    }

    @And("recibe un mensaje de error indicando que el formato del correo no es válido")
    public void mensajeFormatoDeCorreo() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensajeDelCampo("email"), containsString("no es válido")));
    }

    @And("recibe un mensaje de error indicando que el número de celular no es válido")
    public void mensajeCelularInvalido() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensajeDelCampo("cellphone"), containsString("no es válido")));
    }

    @And("recibe un mensaje indicando el requisito de seguridad no cumplido")
    public void mensajePoliticaDeContrasena() {
        theActorCalled(VISITANTE).should(
                seeThat(RespuestaDeLaApi.mensajeDelCampo("password"), containsString("política de seguridad")));
    }

    @And("recibe un mensaje de error específico para el campo {string}")
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
