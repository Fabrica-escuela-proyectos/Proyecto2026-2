package com.codefactory.reservas_backend.e2e.user;

import com.codefactory.reservas_backend.e2e.models.Credenciales;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.IniciarSesion;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarCuenta;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import net.serenitybdd.screenplay.Actor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

/** Preparación de cuentas para las historias que las necesitan como punto de partida (HU-04, HU-05, HU-06...). */
public final class CuentasDePrueba {

    private CuentasDePrueba() {
    }

    /** Crea un administrador directo en la base (no hay forma de registrarlo por la API). Aún sin verificación adicional. */
    public static User crearAdministrador(UserRepository usuarios, RoleRepository roles, PasswordEncoder codificador) {
        Role rolAdministrador = roles.findByName(RoleName.ADMINISTRADOR).orElseThrow();
        return usuarios.save(User.builder()
                .fullName("Administradora de Prueba")
                .email(DatosDeRegistro.nuevoCorreo("admin"))
                .cellphone(DatosDeRegistro.nuevoCelular())
                .passwordHash(codificador.encode(DatosDeRegistro.CONTRASENA_VALIDA))
                .roles(Set.of(rolAdministrador))
                .build());
    }

    public static Credenciales credencialesDe(User usuario) {
        return new Credenciales(usuario.getEmail(), DatosDeRegistro.CONTRASENA_VALIDA, null);
    }

    /** Registra un Cliente o un Proveedor por la API y deja recordados su id, su negocio (si tiene) y sus credenciales. */
    public static void registrar(Actor actor, String rol) {
        boolean proveedor = "Proveedor".equals(rol);
        DatosDeRegistro datos = proveedor ? DatosDeRegistro.validos().comoProveedor() : DatosDeRegistro.validos();
        actor.attemptsTo(proveedor ? RegistrarCuenta.comoProveedor(datos) : RegistrarCuenta.comoCliente(datos));
        actor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        actor.remember(Memoria.ID_DE_USUARIO, RespuestaDeLaApi.idDelUsuario().answeredBy(actor));
        if (proveedor) {
            actor.remember(Memoria.ID_DE_NEGOCIO, RespuestaDeLaApi.idDelNegocio().answeredBy(actor));
        }
        actor.remember(Memoria.ROL, rol);
        actor.remember(Memoria.CREDENCIALES, new Credenciales(datos.correo(), datos.contrasena(), null));
    }

    /** Igual que registrar, y además inicia sesión (queda recordada su sesión). */
    public static void registrarEIniciarSesion(Actor actor, String rol) {
        registrar(actor, rol);
        actor.attemptsTo(IniciarSesion.con(actor.recall(Memoria.CREDENCIALES)));
        actor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }
}
