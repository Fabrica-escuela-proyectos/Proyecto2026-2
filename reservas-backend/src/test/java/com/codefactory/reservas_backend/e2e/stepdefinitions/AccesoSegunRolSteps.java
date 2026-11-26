package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.CambiarRol;
import com.codefactory.reservas_backend.e2e.tasks.CerrarSesion;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarDatosDe;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarSusDatos;
import com.codefactory.reservas_backend.e2e.tasks.CrearRecurso;
import com.codefactory.reservas_backend.e2e.tasks.EliminarUsuario;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;

import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_USUARIO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-06 - Acceso según el rol. "se le deniega el acceso" y "la operación es permitida" están en ComunesSteps.
 * Como "gestionar" se usan operaciones de referencia: consultar datos de una cuenta y crear un recurso en un negocio.
 */
public class AccesoSegunRolSteps {

    // ---------- Given ----------

    @Given("a user with the role {string} has logged in")
    public void usuarioConRolHaIniciadoSesion(String rol) {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(USUARIO), rol);
    }

    @Given("a user has a session that is {string}")
    public void usuarioConUnaSesion(String estado) {
        Actor usuario = theActorCalled(USUARIO);
        if ("revoked".equals(estado)) {
            CuentasDePrueba.registrarEIniciarSesion(usuario, "Cliente");
            usuario.attemptsTo(CerrarSesion.ahora());
            usuario.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
        } else {
            usuario.remember(Memoria.TOKEN, "");
        }
    }

    // ---------- When ----------

    @When("they try to view data that belongs to them")
    public void consultaUnDatoPropio() {
        theActorCalled(USUARIO).attemptsTo(ConsultarSusDatos.conSuSesion());
    }

    @When("they try to view data that does not belong to them")
    public void consultaUnDatoAjeno() {
        String idAjeno = crearOtroUsuario();
        theActorCalled(USUARIO).attemptsTo(ConsultarDatosDe.elUsuario(idAjeno));
    }

    @When("they try to manage the resources of their own business")
    public void gestionaRecursosPropios() {
        Actor usuario = theActorCalled(USUARIO);
        String negocio = usuario.recall(Memoria.ID_DE_NEGOCIO);
        usuario.attemptsTo(CrearRecurso.enElNegocio(negocio));
    }

    @When("they try to manage the resources of another provider")
    public void gestionaRecursosAjenos() {
        String negocioAjeno = crearOtroProveedor();
        theActorCalled(USUARIO).attemptsTo(CrearRecurso.enElNegocio(negocioAjeno));
    }

    @When("they try to {string}")
    public void intentaUnaFuncionalidad(String funcionalidad) {
        switch (funcionalidad) {
            case "create a resource in a business" -> {
                String negocioAjeno = crearOtroProveedor();
                theActorCalled(USUARIO).attemptsTo(CrearRecurso.enElNegocio(negocioAjeno));
            }
            case "delete another user" -> {
                String idAjeno = crearOtroUsuario();
                theActorCalled(USUARIO).attemptsTo(EliminarUsuario.con(idAjeno, null));
            }
            case "assign a role to another user" -> {
                String idAjeno = crearOtroUsuario();
                theActorCalled(USUARIO).attemptsTo(CambiarRol.de(idAjeno, "CLIENTE", null));
            }
            default -> throw new IllegalArgumentException("Funcionalidad desconocida: " + funcionalidad);
        }
    }

    @When("they try to perform a protected operation")
    public void intentaUnaOperacionProtegida() {
        theActorCalled(USUARIO).attemptsTo(ConsultarSusDatos.conSuSesion());
    }

    // ---------- And ----------

    /** Un 401 (y no un 403) significa que la plataforma le pide autenticarse otra vez. */
    @And("they must authenticate again")
    public void debeAutenticarseNuevamente() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(401)));
    }

    // ---------- Apoyo ----------

    private String crearOtroUsuario() {
        Actor otro = theActorCalled(OTRO_USUARIO);
        CuentasDePrueba.registrar(otro, "Cliente");
        return otro.recall(Memoria.ID_DE_USUARIO);
    }

    private String crearOtroProveedor() {
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrar(otro, "Proveedor");
        return otro.recall(Memoria.ID_DE_NEGOCIO);
    }
}
