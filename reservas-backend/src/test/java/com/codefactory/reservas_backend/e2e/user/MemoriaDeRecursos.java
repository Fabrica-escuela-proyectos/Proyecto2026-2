package com.codefactory.reservas_backend.e2e.user;

/** Claves de lo que se recuerda en los escenarios del catálogo y de los recursos (HU-13 a HU-17). */
public final class MemoriaDeRecursos {

    /** Recurso sobre el que actúa el escenario (el propio, uno ajeno o uno inexistente). */
    public static final String ID_DEL_RECURSO = "idDelRecurso";
    public static final String ID_DEL_NEGOCIO_DEL_RECURSO = "idDelNegocioDelRecurso";
    public static final String NOMBRE_DEL_RECURSO = "nombreDelRecurso";
    public static final String TIPO_DEL_RECURSO = "tipoDelRecurso";
    /** Estado activo/inactivo que tenía el recurso antes de la acción (Boolean). */
    public static final String ACTIVO_ANTES = "activoAntes";
    /** Cantidad de recursos del negocio antes de la acción (Integer). */
    public static final String CANTIDAD_ANTES = "cantidadAntes";
    /** Lo que el proveedor escribió en el formulario antes de confirmar (DatosDeRecurso). */
    public static final String BORRADOR = "borrador";
    public static final String ID_DEL_NEGOCIO_AJENO = "idDelNegocioAjeno";
    /** Otro recurso (existente) que el escenario comprueba que no cambió. */
    public static final String ID_DE_OTRO_RECURSO = "idDeOtroRecurso";
    /** HU-13: nombres de los negocios que debe mostrar el catálogo, el negocio consultado y sus servicios. */
    public static final String NOMBRES_DE_NEGOCIOS = "nombresDeNegocios";
    public static final String ID_DEL_NEGOCIO_CONSULTADO = "idDelNegocioConsultado";
    public static final String SERVICIOS_ESPERADOS = "serviciosEsperados";

    private MemoriaDeRecursos() {
    }
}
