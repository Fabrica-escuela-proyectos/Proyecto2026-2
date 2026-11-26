package com.codefactory.reservas_backend.e2e.user;

/** Claves de lo que recuerda el proveedor dueño en los escenarios de negocio y servicios (HU-07 a HU-12). */
public final class MemoriaDeServicios {

    public static final String ID_DE_SERVICIO = "idDeServicio";
    /** Duración del servicio, para que el cliente reserve con la duración correcta. */
    public static final String DURACION = "duracionDelServicio";
    /** Ids de los servicios del escenario por nombre (Map<String, String>). */
    public static final String SERVICIOS_POR_NOMBRE = "serviciosPorNombre";
    /** Copia del servicio o del negocio antes de la acción, para comprobar que no cambió. */
    public static final String SERVICIO_ANTES = "servicioAntes";
    public static final String NEGOCIO_ANTES = "negocioAntes";
    /** Cuántos servicios tenía el negocio antes de la acción. */
    public static final String CANTIDAD_ANTES = "cantidadAntes";
    /** Lo que se envió y que debería quedar guardado. */
    public static final String DATOS_ESPERADOS = "datosEsperados";
    public static final String ANTELACION_ESPERADA = "antelacionEsperada";
    /** Segundo negocio del mismo proveedor (HU-07 y HU-08). */
    public static final String OTRO_NEGOCIO = "otroNegocio";
    public static final String OTRO_NEGOCIO_ANTES = "otroNegocioAntes";
    public static final String AGENDA = "agendaDelServicio";
    /** Ids de las reservas del escenario (List<String>). */
    public static final String RESERVAS = "reservasDelServicio";
    public static final String RESERVA_PASADA = "reservaPasada";
    public static final String RESERVAS_ANTES = "reservasAntesDelCambio";

    private MemoriaDeServicios() {
    }
}
