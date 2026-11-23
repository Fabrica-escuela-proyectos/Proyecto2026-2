package com.codefactory.reservas_backend.e2e.user;

/** Claves de lo que se recuerda en los escenarios de reservas y reportes (HU-24 a HU-28 y HU-33). */
public final class MemoriaDeReservas {

    /** Agenda (negocio, servicio y recurso) del proveedor del escenario. */
    public static final String AGENDA = "agenda";
    /** Ids de las reservas que revisa el escenario (List<String>); "la reserva" es la primera. */
    public static final String RESERVAS = "reservas";
    /** Copia de cada reserva antes de la acción (Map<String, Booking>), para comprobar que no cambió. */
    public static final String RESERVAS_ANTES = "reservasAntes";
    /** Horario de la reserva que se cancela, para buscarlo luego en la disponibilidad. */
    public static final String HORARIO = "horario";
    /** Id de la cuenta que eliminará el administrador (HU-28). */
    public static final String CUENTA_A_ELIMINAR = "cuentaAEliminar";
    public static final String MOTIVO = "motivo";
    public static final String ESTADO_PEDIDO = "estadoPedido";
    /** Periodo del reporte (HU-33): fechas como texto, tal cual se envían. */
    public static final String DESDE = "desde";
    public static final String HASTA = "hasta";
    public static final String VALOR_ESPERADO = "valorEsperado";
    public static final String CANTIDAD_ESPERADA = "cantidadEsperada";

    private MemoriaDeReservas() {
    }
}
