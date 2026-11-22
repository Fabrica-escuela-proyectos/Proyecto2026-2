package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Roles;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarCuenta;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.codefactory.reservas_backend.e2e.user.Personajes.PROVEEDOR_EXISTENTE;
import static com.codefactory.reservas_backend.e2e.user.Personajes.VISITANTE;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * HU-03 - Registro de proveedor. Los casos que son iguales a los del cliente (correo ya usado, campo obligatorio,
 * mensajes de error) reutilizan los pasos de RegistroClienteSteps, que son los mismos textos.
 */
public class RegistroProveedorSteps {

    @Autowired
    private UserRepository userRepository;

    // ---------- Given ----------

    @Given("an unregistered visitor wants to create a provider account")
    public void visitanteQuiereCrearCuentaDeProveedor() {
        theActorCalled(VISITANTE).remember(Memoria.DATOS, DatosDeRegistro.validos().comoProveedor());
    }

    @Given("a provider account is already registered")
    public void yaExisteUnaCuentaDeProveedor() {
        Actor existente = theActorCalled(PROVEEDOR_EXISTENTE);
        existente.attemptsTo(RegistrarCuenta.comoProveedor(DatosDeRegistro.validos().comoProveedor()));
        existente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        visitanteQuiereCrearCuentaDeProveedor();
    }

    // ---------- When ----------

    @When("they complete all personal and business data with valid information and submit the request")
    public void completaTodosLosDatosYEnvia() {
        enviar();
    }

    @When("they finish registering with valid data")
    public void terminaElRegistro() {
        enviar();
    }

    @When("a visitor tries to register as a provider with the same email as that account")
    public void intentaRegistrarseConElMismoCorreo() {
        DatosDeRegistro existente = theActorCalled(PROVEEDOR_EXISTENTE).recall(Memoria.DATOS);
        Actor visitante = theActorCalled(VISITANTE);
        visitante.attemptsTo(RegistrarCuenta.comoProveedor(
                DatosDeRegistro.validos().comoProveedor().conCorreo(existente.correo())));
    }

    @When("they try to register with the role {string}")
    public void intentaRegistrarseConElRol(String rol) {
        theActorCalled(VISITANTE).attemptsTo(RegistrarCuenta.comoProveedorSolicitandoRol(datosDelVisitante(), rol));
    }

    // ---------- Then / And ----------

    @Then("the provider account is created successfully")
    public void laCuentaDelProveedorQuedaCreada() {
        theActorCalled(VISITANTE).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        assertThat(userRepository.findByEmailIgnoreCase(datosDelVisitante().correo())).isPresent();
    }

    @And("the business information is registered and linked to that account")
    public void elNegocioQuedaRegistradoYAsociado() {
        Actor visitante = theActorCalled(VISITANTE);
        visitante.should(seeThat(RespuestaDeLaApi.nombreDelNegocio(), equalTo(datosDelVisitante().nombreDelNegocio())));
        visitante.should(seeThat(RespuestaDeLaApi.idDelNegocio(), not(emptyOrNullString())));
    }

    @Then("they have the role {string} in a controlled way")
    public void tieneElRolDeFormaControlada(String rol) {
        theActorCalled(VISITANTE).should(seeThat(RespuestaDeLaApi.rol(), equalTo(Roles.nombreEnLaApi(rol))));
    }

    @Then("the requested role is ignored and they are given the role {string} in a controlled way")
    public void elRolSolicitadoEsIgnorado(String rol) {
        Actor visitante = theActorCalled(VISITANTE);
        visitante.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        visitante.should(seeThat(RespuestaDeLaApi.rol(), equalTo(Roles.nombreEnLaApi(rol))));
    }

    @And("the account is registered only with the authorized role")
    public void soloConElRolAutorizado() {
        Set<RoleName> roles = userRepository.findByEmailIgnoreCase(datosDelVisitante().correo()).orElseThrow()
                .getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        assertThat(roles).containsExactly(RoleName.PROVEEDOR);
    }

    // ---------- Apoyo ----------

    private DatosDeRegistro datosDelVisitante() {
        return theActorCalled(VISITANTE).recall(Memoria.DATOS);
    }

    private void enviar() {
        theActorCalled(VISITANTE).attemptsTo(RegistrarCuenta.comoProveedor(datosDelVisitante()));
    }
}
