package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Credenciales;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.ActivarVerificacionAdicional;
import com.codefactory.reservas_backend.e2e.tasks.IniciarSesion;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarCuenta;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.codefactory.reservas_backend.support.TotpTestSupport;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Set;

import static com.codefactory.reservas_backend.e2e.user.Personajes.ADMINISTRADOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/** HU-02 - Inicio de sesión (incluye la verificación adicional del Administrador). */
public class InicioSesionSteps {

    private static final String SIN_VALOR = "(vacío)";
    private static final String CORREO_REGISTRADO = "el correo registrado";
    private static final String CONTRASENA_REGISTRADA = "la contraseña registrada";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- Given ----------

    @Given("un cliente registrado quiere iniciar sesión")
    public void clienteRegistradoQuiereIniciarSesion() {
        Actor actor = theActorCalled(USUARIO);
        DatosDeRegistro datos = DatosDeRegistro.validos();
        actor.attemptsTo(RegistrarCuenta.comoCliente(datos));
        actor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        actor.remember(Memoria.CREDENCIALES, new Credenciales(datos.correo(), datos.contrasena(), null));
    }

    @Given("un usuario con rol {string} inició sesión exitosamente")
    public void usuarioConRolInicioSesion(String rol) {
        Actor usuario = theActorCalled(USUARIO);
        DatosDeRegistro datos = DatosDeRegistro.validos();
        if ("Proveedor".equals(rol)) {
            datos = datos.comoProveedor();
            usuario.attemptsTo(RegistrarCuenta.comoProveedor(datos));
        } else {
            usuario.attemptsTo(RegistrarCuenta.comoCliente(datos));
        }
        usuario.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        usuario.attemptsTo(IniciarSesion.con(new Credenciales(datos.correo(), datos.contrasena(), null)));
        usuario.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    @Given("un Administrador con verificación adicional activa ingresa su correo y contraseña correctos")
    public void administradorConVerificacionActiva() {
        String correo = DatosDeRegistro.nuevoCorreo("admin");
        Role rolAdministrador = roleRepository.findByName(RoleName.ADMINISTRADOR).orElseThrow();
        userRepository.save(User.builder()
                .fullName("Administradora de Prueba")
                .email(correo)
                .cellphone(DatosDeRegistro.nuevoCelular())
                .passwordHash(passwordEncoder.encode(DatosDeRegistro.CONTRASENA_VALIDA))
                .roles(Set.of(rolAdministrador))
                .build());

        Credenciales sinCodigo = new Credenciales(correo, DatosDeRegistro.CONTRASENA_VALIDA, null);
        Actor administrador = theActorCalled(ADMINISTRADOR);
        // Un administrador recién creado aún no tiene la verificación activa: entra y la completa.
        administrador.attemptsTo(IniciarSesion.con(sinCodigo));
        administrador.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        administrador.attemptsTo(ActivarVerificacionAdicional.conSuAplicacion());
        administrador.remember(Memoria.CREDENCIALES, sinCodigo);
    }

    // ---------- When ----------

    @When("proporciona su correo y contraseña correctos")
    public void proporcionaCredencialesCorrectas() {
        Actor usuario = theActorCalled(USUARIO);
        usuario.attemptsTo(IniciarSesion.con(usuario.recall(Memoria.CREDENCIALES)));
    }

    @When("intenta iniciar sesión con el correo {string} y la contraseña {string}")
    public void intentaIniciarSesionCon(String correo, String contrasena) {
        Actor usuario = theActorCalled(USUARIO);
        Credenciales registradas = usuario.recall(Memoria.CREDENCIALES);
        usuario.attemptsTo(IniciarSesion.con(new Credenciales(
                resolver(correo, CORREO_REGISTRADO, registradas.correo()),
                resolver(contrasena, CONTRASENA_REGISTRADA, registradas.contrasena()),
                null)));
    }

    @When("su cuenta es reconocida")
    public void suCuentaEsReconocida() {
        // La identificación del rol ocurre en la misma respuesta del inicio de sesión: no hay una acción aparte.
    }

    @When("intenta iniciar sesión sin el código de verificación")
    public void intentaIniciarSesionSinCodigo() {
        Actor administrador = theActorCalled(ADMINISTRADOR);
        administrador.attemptsTo(IniciarSesion.con(credencialesDelAdministrador(administrador).conCodigo(null)));
    }

    @When("completa la verificación con el código vigente")
    public void completaLaVerificacion() {
        Actor administrador = theActorCalled(ADMINISTRADOR);
        String secreto = administrador.recall(Memoria.SECRETO_MFA);
        administrador.attemptsTo(IniciarSesion.con(
                credencialesDelAdministrador(administrador).conCodigo(TotpTestSupport.codeNow(secreto))));
    }

    @When("envía un código de verificación incorrecto")
    public void enviaUnCodigoIncorrecto() {
        Actor administrador = theActorCalled(ADMINISTRADOR);
        String secreto = administrador.recall(Memoria.SECRETO_MFA);
        administrador.attemptsTo(IniciarSesion.con(
                credencialesDelAdministrador(administrador).conCodigo(TotpTestSupport.wrongCode(secreto))));
    }

    // ---------- Then ----------

    @Then("obtiene acceso")
    public void obtieneAcceso() {
        Actor actor = theActorInTheSpotlight();
        actor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        actor.should(seeThat(RespuestaDeLaApi.token(), not(emptyOrNullString())));
    }

    @Then("el intento de acceso es rechazado y no puede ingresar a la plataforma")
    public void accesoRechazado() {
        Actor actor = theActorInTheSpotlight();
        actor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), anyOf(equalTo(400), equalTo(401))));
        actor.should(seeThat(RespuestaDeLaApi.token(), emptyOrNullString()));
    }

    @Then("se identifica el rol {string} asociado al usuario")
    public void seIdentificaElRol(String rol) {
        theActorInTheSpotlight().should(
                seeThat(RespuestaDeLaApi.rol(), equalTo(rol.toUpperCase(Locale.ROOT))));
    }

    @Then("se le solicita completar la verificación adicional")
    public void seSolicitaVerificacionAdicional() {
        Actor actor = theActorInTheSpotlight();
        actor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(401)));
        actor.should(seeThat(RespuestaDeLaApi.codigoDeError(), equalTo("MFA_REQUIRED")));
    }

    @Then("no obtiene acceso")
    public void noObtieneAcceso() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(401)));
    }

    // ---------- Apoyo ----------

    private static Credenciales credencialesDelAdministrador(Actor administrador) {
        return administrador.recall(Memoria.CREDENCIALES);
    }

    /** Traduce los marcadores de las tablas de ejemplos a valores reales. */
    private static String resolver(String valor, String marcador, String registrado) {
        if (SIN_VALOR.equals(valor)) {
            return "";
        }
        return marcador.equals(valor) ? registrado : valor;
    }
}
