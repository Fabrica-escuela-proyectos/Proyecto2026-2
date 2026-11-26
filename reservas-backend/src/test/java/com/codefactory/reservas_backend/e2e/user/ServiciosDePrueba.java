package com.codefactory.reservas_backend.e2e.user;

import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarServicio;
import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.Personajes.CLIENTE_EXISTENTE;
import static com.codefactory.reservas_backend.e2e.user.Personajes.PROVEEDOR_EXISTENTE;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.equalTo;

/**
 * Preparación y consultas de apoyo de las HU-07 a HU-12: el proveedor dueño con su negocio, sus servicios,
 * el cliente que reserva, y la consulta de cómo quedaron guardados los datos.
 */
public final class ServiciosDePrueba {

    public static final int DURACION_VALIDA = 30;
    public static final long PRECIO_VALIDO = 50_000;

    private ServiciosDePrueba() {
    }

    // ---------- Actores ----------

    /** El proveedor dueño. La primera vez que se pide en el escenario se registra e inicia sesión. */
    public static Actor dueno() {
        Actor dueno = theActorCalled(PROVEEDOR_EXISTENTE);
        if (dueno.recall(Memoria.ID_DE_NEGOCIO) == null) {
            CuentasDePrueba.registrarEIniciarSesion(dueno, "Proveedor");
        }
        return dueno;
    }

    /** El cliente del escenario. La primera vez que se pide se registra e inicia sesión. */
    public static Actor cliente() {
        Actor cliente = theActorCalled(CLIENTE_EXISTENTE);
        if (cliente.recall(Memoria.TOKEN) == null) {
            CuentasDePrueba.registrarEIniciarSesion(cliente, "Cliente");
        }
        return cliente;
    }

    public static String negocioDelDueno() {
        return dueno().recall(Memoria.ID_DE_NEGOCIO);
    }

    public static String servicioDelDueno() {
        return dueno().recall(MemoriaDeServicios.ID_DE_SERVICIO);
    }

    /** Vuelve a poner en el foco al actor que va a actuar (theActorCalled cambia el foco). */
    public static Actor enElFoco(Actor actor) {
        return theActorCalled(actor.getName());
    }

    // ---------- Datos ----------

    /** Datos válidos de un servicio, con un nombre que no se repite. */
    public static Map<String, Object> datosValidos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("name", "Servicio " + UUID.randomUUID().toString().substring(0, 8));
        datos.put("durationMinutes", DURACION_VALIDA);
        datos.put("priceCop", PRECIO_VALIDO);
        return datos;
    }

    /** Nombre del campo en la API para cada palabra del feature. */
    public static String campoEnLaApi(String campo) {
        return switch (campo) {
            case "name" -> "name";
            case "description" -> "description";
            case "duration" -> "durationMinutes";
            case "price" -> "priceCop";
            default -> throw new IllegalArgumentException("Campo desconocido en el feature: " + campo);
        };
    }

    /** "30" se envía como número; "abc" o "" se envían como texto, tal cual. */
    public static Object valor(String texto) {
        try {
            return Long.parseLong(texto.trim());
        } catch (NumberFormatException noEsNumero) {
            return texto;
        }
    }

    // ---------- Preparación por la API ----------

    /** El dueño crea un servicio con esos datos y se comprueba que quedó creado; devuelve su id. */
    public static String crearServicio(Map<String, Object> datos) {
        Actor dueno = dueno();
        dueno.attemptsTo(RegistrarServicio.conLosDatos(negocioDelDueno(), datos));
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String id = LastResponse.received().answeredBy(dueno).jsonPath().getString("id");
        dueno.remember(MemoriaDeServicios.ID_DE_SERVICIO, id);
        dueno.remember(MemoriaDeServicios.DURACION, ((Number) datos.get("durationMinutes")).intValue());
        return id;
    }

    public static String crearServicioValido() {
        return crearServicio(datosValidos());
    }

    /**
     * Inserta un segundo negocio del mismo proveedor directo en la base: el modelo permite varios negocios por
     * proveedor, pero la API solo crea el primero (al registrarse). Igual que CuentasDePrueba crea al administrador.
     */
    public static String crearSegundoNegocio(BusinessRepository negocios, String idDelPrimerNegocio) {
        Business primero = negocios.findById(UUID.fromString(idDelPrimerNegocio)).orElseThrow();
        Business segundo = negocios.save(Business.builder()
                .providerId(primero.getProviderId())
                .name("Segundo negocio " + UUID.randomUUID().toString().substring(0, 6))
                .build());
        return segundo.getId().toString();
    }

    // ---------- Cómo quedaron los datos (consulta directa a la base de datos) ----------
    //
    // Para comprobar que algo NO cambió se consulta la base con los repositorios, igual que el equipo revisa las
    // reservas con BookingRepository. No se hace por la API porque Serenity guarda una sola "última respuesta":
    // una consulta extra borraría la respuesta de error del proveedor, que el paso siguiente todavía revisa.

    private static ServiceOfferingRepository servicios;
    private static BusinessRepository negocios;

    /** Lo llama un @Before de ServiciosComunesSteps, que recibe los repositorios de Spring. */
    public static void usarLaBaseDeDatos(ServiceOfferingRepository repositorioDeServicios,
                                         BusinessRepository repositorioDeNegocios) {
        servicios = repositorioDeServicios;
        negocios = repositorioDeNegocios;
    }

    /** El servicio tal como quedó guardado, o null si no existe. */
    public static Map<String, Object> servicioGuardado(String idDelServicio) {
        return servicios.findById(UUID.fromString(idDelServicio)).map(ServiciosDePrueba::comoMapa).orElse(null);
    }

    private static Map<String, Object> comoMapa(ServiceOffering servicio) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", servicio.getId().toString());
        datos.put("name", servicio.getName());
        datos.put("description", servicio.getDescription());
        datos.put("durationMinutes", servicio.getDurationMinutes());
        datos.put("priceCop", servicio.getPriceCop());
        datos.put("active", servicio.isActive());
        datos.put("createdAt", servicio.getCreatedAt());
        return datos;
    }

    private static List<ServiceOffering> serviciosDelNegocio() {
        String negocio = theActorCalled(PROVEEDOR_EXISTENTE).recall(Memoria.ID_DE_NEGOCIO);
        return servicios.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(negocio));
    }

    public static int cantidadDeServicios() {
        return serviciosDelNegocio().size();
    }

    public static long serviciosLlamados(String nombre) {
        return serviciosDelNegocio().stream().filter(servicio -> nombre.equalsIgnoreCase(servicio.getName())).count();
    }

    /** Los cinco datos editables del negocio tal como quedaron guardados. */
    public static Map<String, String> negocioGuardado(String idDelNegocio) {
        Business negocio = negocios.findById(UUID.fromString(idDelNegocio)).orElseThrow();
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("name", negocio.getName());
        datos.put("description", negocio.getDescription());
        datos.put("address", negocio.getAddress());
        datos.put("businessType", negocio.getBusinessType() == null ? null : negocio.getBusinessType().name());
        datos.put("phone", negocio.getPhone());
        return datos;
    }

    public static int antelacionGuardada(String idDelNegocio) {
        return negocios.findById(UUID.fromString(idDelNegocio)).orElseThrow().getMinAdvanceHours();
    }
}
