package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Roles;
import com.codefactory.reservas_backend.e2e.models.Credenciales;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.AutenticarseComoAdministrador;
import com.codefactory.reservas_backend.e2e.tasks.CambiarRol;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarDatosDe;
import com.codefactory.reservas_backend.e2e.tasks.EliminarUsuario;
import com.codefactory.reservas_backend.e2e.tasks.IniciarSesion;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.codefactory.reservas_backend.support.TotpTestSupport;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.codefactory.reservas_backend.e2e.user.Personajes.ADMINISTRADOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OBJETIVO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-05 - Gestionar roles y permisos (y el escenario de operación sensible de HU-02).
 * Actores: el Administrador, el "Usuario objetivo" sobre el que actúa y, en la auto-modificación, el "Usuario".
 */
public class GestionarRolesYPermisosSteps {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- Given ----------

    @Given("an authenticated Administrator and a user with the role {string}")
    public void administradorYUsuarioConRol(String rol) {
        autenticarAdministrador();
        crearObjetivo(rol);
    }

    @Given("a user received the role {string} and has not yet completed the creation of additional verification")
    public void usuarioAscendidoSinVerificacion(String rol) {
        autenticarAdministrador();
        crearObjetivo("Cliente");
        Actor administrador = theActorCalled(ADMINISTRADOR);
        administrador.attemptsTo(CambiarRol.de(idDelObjetivo(), Roles.nombreEnLaApi(rol), codigoVigente(administrador)));
        administrador.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    @Given("a user with the role {string} was deleted")
    public void usuarioEliminado(String rol) {
        autenticarAdministrador();
        crearObjetivo(rol);
        Actor administrador = theActorCalled(ADMINISTRADOR);
        administrador.attemptsTo(EliminarUsuario.con(idDelObjetivo(), codigoVigente(administrador)));
        administrador.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
    }

    @Given("an authenticated user with the role {string}")
    public void usuarioAutenticadoConRol(String rol) {
        Actor usuario = theActorCalled(USUARIO);
        if ("Administrador".equals(rol)) {
            User administrador = CuentasDePrueba.crearAdministrador(userRepository, roleRepository, passwordEncoder);
            usuario.attemptsTo(AutenticarseComoAdministrador.conVerificacion(CuentasDePrueba.credencialesDe(administrador)));
            usuario.remember(Memoria.ID_DE_USUARIO, administrador.getId().toString());
            usuario.remember(Memoria.ROL, rol);
        } else {
            CuentasDePrueba.registrarEIniciarSesion(usuario, rol);
        }
    }

    // ---------- When ----------

    @When("they assign the role {string} to that user with their additional confirmation")
    public void asignaElRol(String rol) {
        cambiarRolDelObjetivo(Roles.nombreEnLaApi(rol), true);
    }

    @When("they try to change that user's role with their additional confirmation")
    public void intentaCambiarleElRol() {
        cambiarRolDelObjetivo("CLIENTE", true);
    }

    @When("they try to assign the nonexistent role {string} with their additional confirmation")
    public void intentaAsignarUnRolInexistente(String rol) {
        cambiarRolDelObjetivo(Roles.nombreEnLaApi(rol), true);
    }

    @When("they try to change that user's permissions without the additional confirmation")
    public void intentaModificarSinConfirmacion() {
        cambiarRolDelObjetivo("ADMINISTRADOR", false);
    }

    @When("they delete that user with their additional confirmation")
    public void eliminaAEseUsuario() {
        eliminarObjetivo();
    }

    @When("they try to delete that user with their additional confirmation")
    public void intentaEliminarAEseUsuario() {
        eliminarObjetivo();
    }

    @When("they try to log in with their previous credentials")
    public void intentaIniciarSesionConSusCredencialesAnteriores() {
        Actor objetivo = theActorCalled(OBJETIVO);
        objetivo.attemptsTo(IniciarSesion.con(objetivo.recall(Memoria.CREDENCIALES)));
    }

    @When("they try to access as an Administrator")
    public void intentaAccederComoAdministrador() {
        Actor objetivo = theActorCalled(OBJETIVO);
        objetivo.attemptsTo(IniciarSesion.con(objetivo.recall(Memoria.CREDENCIALES)));
        objetivo.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        objetivo.attemptsTo(ConsultarDatosDe.elUsuario(idDelObjetivo()));
    }

    @When("they try to modify their own permissions or role")
    public void intentaModificarSusPropiosPermisos() {
        Actor usuario = theActorCalled(USUARIO);
        String id = usuario.recall(Memoria.ID_DE_USUARIO);
        String rolActual = usuario.recall(Memoria.ROL);
        // El administrador intenta bajarse a Cliente; los demás intentan subirse a Administrador.
        String rolNuevo = "Administrador".equals(rolActual) ? "CLIENTE" : "ADMINISTRADOR";
        usuario.attemptsTo(CambiarRol.de(id, rolNuevo, codigoVigente(usuario)));
    }

    // ---------- Then / And ----------

    @Then("the user's role is updated to {string}")
    public void elRolQuedaActualizado(String rol) {
        Actor administrador = theActorCalled(ADMINISTRADOR);
        administrador.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        administrador.should(seeThat(RespuestaDeLaApi.rol(), equalTo(Roles.nombreEnLaApi(rol))));
        assertThat(rolesEnLaBase(idDelObjetivo())).containsExactly(RoleName.valueOf(Roles.nombreEnLaApi(rol)));
    }

    @Then("access is denied until they complete the creation of additional verification")
    public void accesoDenegadoHastaCompletarLaVerificacion() {
        Actor objetivo = theActorCalled(OBJETIVO);
        objetivo.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)));
        objetivo.should(seeThat(RespuestaDeLaApi.codigoDeError(), equalTo("MFA_ENROLLMENT_REQUIRED")));
    }

    @Then("the operation is rejected and the role does not change")
    public void rechazadaYElRolNoCambia() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)));
        RoleName original = RoleName.valueOf(Roles.nombreEnLaApi((String) theActorCalled(OBJETIVO).recall(Memoria.ROL)));
        assertThat(rolesEnLaBase(idDelObjetivo())).containsExactly(original);
    }

    @And("they receive a message saying that the role of users with the role {string} cannot be changed")
    public void mensajeRolInmutable(String rol) {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.mensaje(), containsString("rol " + Roles.etiquetaEnLaApi(rol))));
    }

    @Then("the user is completely deleted")
    public void elUsuarioQuedaEliminado() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
        assertThat(userRepository.findById(UUID.fromString(idDelObjetivo()))).isEmpty();
    }

    @Then("the operation is rejected and the user is not deleted")
    public void rechazadaYElUsuarioNoSeElimina() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)));
        assertThat(userRepository.findById(UUID.fromString(idDelObjetivo()))).isPresent();
    }

    @Then("the operation is rejected")
    public void laOperacionEsRechazada() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), anyOf(equalTo(400), equalTo(403))));
    }

    @Then("they cannot perform the action")
    public void noPuedeRealizarLaAccion() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), anyOf(equalTo(401), equalTo(403))));
    }

    @And("they receive an error message saying the role does not exist")
    public void mensajeRolInexistente() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.mensaje(), containsString("no existe")));
    }

    @And("they receive an error message saying they cannot modify themselves")
    public void mensajeAutoModificacion() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.mensaje(), containsString("propio rol")));
    }

    /** Cliente y Proveedor: Spring Security los rechaza antes de la regla de auto-modificación (403 genérico). */
    @And("they receive an error message saying they do not have permission for this operation")
    public void mensajeSinPermisos() {
        theActorInTheSpotlight().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)),
                seeThat(RespuestaDeLaApi.mensaje(), containsString("No tiene permisos")));
    }

    // ---------- Apoyo ----------

    private void autenticarAdministrador() {
        User administrador = CuentasDePrueba.crearAdministrador(userRepository, roleRepository, passwordEncoder);
        theActorCalled(ADMINISTRADOR)
                .attemptsTo(AutenticarseComoAdministrador.conVerificacion(CuentasDePrueba.credencialesDe(administrador)));
    }

    private void crearObjetivo(String rol) {
        Actor objetivo = theActorCalled(OBJETIVO);
        if ("Administrador".equals(rol)) {
            User otroAdministrador = CuentasDePrueba.crearAdministrador(userRepository, roleRepository, passwordEncoder);
            objetivo.remember(Memoria.ID_DE_USUARIO, otroAdministrador.getId().toString());
            objetivo.remember(Memoria.CREDENCIALES, CuentasDePrueba.credencialesDe(otroAdministrador));
            objetivo.remember(Memoria.ROL, rol);
        } else {
            CuentasDePrueba.registrar(objetivo, rol);
        }
    }

    private String idDelObjetivo() {
        return theActorCalled(OBJETIVO).recall(Memoria.ID_DE_USUARIO);
    }

    private static String codigoVigente(Actor actor) {
        String secreto = actor.recall(Memoria.SECRETO_MFA);
        return secreto == null ? null : TotpTestSupport.codeNow(secreto);
    }

    private void cambiarRolDelObjetivo(String rol, boolean conConfirmacion) {
        String id = idDelObjetivo();
        Actor administrador = theActorCalled(ADMINISTRADOR);
        administrador.attemptsTo(CambiarRol.de(id, rol, conConfirmacion ? codigoVigente(administrador) : null));
    }

    private void eliminarObjetivo() {
        String id = idDelObjetivo();
        Actor administrador = theActorCalled(ADMINISTRADOR);
        administrador.attemptsTo(EliminarUsuario.con(id, codigoVigente(administrador)));
    }

    private Set<RoleName> rolesEnLaBase(String idDelUsuario) {
        return userRepository.findById(UUID.fromString(idDelUsuario)).orElseThrow()
                .getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    }
}
