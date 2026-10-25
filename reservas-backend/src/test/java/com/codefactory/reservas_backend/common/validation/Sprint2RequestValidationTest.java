package com.codefactory.reservas_backend.common.validation;

import com.codefactory.reservas_backend.provider.controller.dto.BookingLeadTimeRequest;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CancelBookingRequest;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CreateBookingRequest;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.ProviderCancelRequest;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.DayRangesRequest;
import com.codefactory.reservas_backend.resource.controller.dto.AvailabilityDtos.TimeRange;
import com.codefactory.reservas_backend.resource.controller.dto.CreateResourceRequest;
import com.codefactory.reservas_backend.service.controller.dto.CreateServiceRequest;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResourcesDtos.AssignResourcesRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas UNITARIAS puras (sin Spring, sin base de datos, sin Docker) de las reglas de validación de entrada
 * del Sprint 2: HU-08, HU-09, HU-14, HU-18, HU-19, HU-22, HU-25 y HU-26. Cada prueba indica el caso del plan
 * de pruebas que respalda. Las reglas que dependen de la base de datos, del usuario o de otros servicios
 * (rangos que se traslapan, nombre repetido, pertenencia del negocio...) se prueban en las clases *ServiceImplTest
 * y *IntegrationTest; los valores que Jackson rechaza antes de validar (por ejemplo "abc" en un campo numérico)
 * se prueban en las pruebas de integración, porque dependen del deserializador.
 */
class Sprint2RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<String> fields(Object bean) {
        return validator.validate(bean).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static List<String> messages(Object bean) {
        return validator.validate(bean).stream().map(ConstraintViolation::getMessage).toList();
    }

    private static String text(int length) {
        return "x".repeat(length);
    }

    // ---------------------------------------------------------------- HU-08 (CP-HU08-01)

    private static BookingLeadTimeRequest lead(Integer hours) {
        BookingLeadTimeRequest request = new BookingLeadTimeRequest();
        request.setHours(hours);
        return request;
    }

    /** CP-HU08-01 (camino feliz) - 1 h (por defecto), 2 h y el tope de 720 h son válidos. */
    @Test
    void laAntelacionValidaYSusLimitesSeAceptan() {
        for (int hours : new int[]{1, 2, 720}) {
            assertThat(fields(lead(hours))).as("%s horas", hours).isEmpty();
        }
    }

    /** CP-HU08-01-E1/E2 - 0 y negativos se rechazan (también 721 y la ausencia del valor). */
    @Test
    void laAntelacionCeroNegativaExcedidaOAusenteSeRechaza() {
        for (Integer hours : new Integer[]{0, -2, 721, null}) {
            assertThat(fields(lead(hours))).as("%s horas", hours).containsExactly("hours");
        }
        assertThat(messages(lead(0))).containsExactly("La antelación mínima debe ser un número entero de horas mayor o igual a 1");
    }

    // ---------------------------------------------------------------- HU-09 (CP-HU09-01)

    private static CreateServiceRequest service(String name, Integer duration, Long price) {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName(name);
        request.setDurationMinutes(duration);
        request.setPriceCop(price);
        return request;
    }

    /** CP-HU09-01 / CP-HU09-02 (camino feliz) - Datos válidos, límites incluidos y servicio gratuito (precio 0). */
    @Test
    void unServicioValidoSeAceptaIncluidoElPrecioCeroYLosLimites() {
        assertThat(fields(service("Corte de cabello", 45, 35_000L))).isEmpty();
        assertThat(fields(service("Primera valoración", 30, 0L))).isEmpty();
        assertThat(fields(service("Mínimo", 1, 0L))).isEmpty();
        assertThat(fields(service("Máximo", 1440, 1_000_000_000L))).isEmpty();
        assertThat(fields(service(text(150), 30, 1_000L))).isEmpty();
    }

    /** CP-HU09-01-E1 - Sin nombre (ausente, vacío o en blanco) se rechaza señalando el nombre. */
    @Test
    void elNombreEsObligatorio() {
        for (String name : new String[]{null, "", "   "}) {
            assertThat(fields(service(name, 45, 35_000L))).as("nombre '%s'", name).containsExactly("name");
        }
        assertThat(messages(service(null, 45, 35_000L))).containsExactly("El nombre del servicio es obligatorio");
    }

    /** CP-HU09-01-E2 - Sin duración se rechaza señalando la duración. */
    @Test
    void laDuracionEsObligatoria() {
        assertThat(fields(service("Corte", null, 35_000L))).containsExactly("durationMinutes");
        assertThat(messages(service("Corte", null, 35_000L))).containsExactly("La duración es obligatoria");
    }

    /** CP-HU09-01-E3 - Sin precio se rechaza señalando el precio. */
    @Test
    void elPrecioEsObligatorio() {
        assertThat(fields(service("Corte", 45, null))).containsExactly("priceCop");
        assertThat(messages(service("Corte", 45, null))).containsExactly("El precio es obligatorio");
    }

    /** CP-HU09-01-E4/E5 - Duración 0 o negativa: el mensaje la declara inválida; también se rechaza más de 24 h. */
    @Test
    void laDuracionCeroNegativaOMayorAUnDiaSeRechaza() {
        for (int duration : new int[]{0, -30, 1441}) {
            assertThat(fields(service("Corte", duration, 35_000L))).as("duración %s", duration).containsExactly("durationMinutes");
        }
        assertThat(messages(service("Corte", 0, 35_000L)))
                .containsExactly("La duración debe ser un número entero de minutos mayor que 0");
        assertThat(messages(service("Corte", -30, 35_000L)))
                .containsExactly("La duración debe ser un número entero de minutos mayor que 0");
    }

    /** CP-HU09-01-E7 - Precio negativo: el mensaje lo declara inválido. */
    @Test
    void elPrecioNegativoSeRechaza() {
        assertThat(fields(service("Corte", 45, -5_000L))).containsExactly("priceCop");
        assertThat(messages(service("Corte", 45, -5_000L))).containsExactly("El precio no puede ser negativo");
    }

    /** Límites de texto del servicio: nombre de 151 y descripción de 501 caracteres. */
    @Test
    void elNombreYLaDescripcionTienenTope() {
        assertThat(fields(service(text(151), 30, 1_000L))).containsExactly("name");
        CreateServiceRequest longDescription = service("Corte", 30, 1_000L);
        longDescription.setDescription(text(501));
        assertThat(fields(longDescription)).containsExactly("description");
        longDescription.setDescription(text(500));
        assertThat(fields(longDescription)).isEmpty();
    }

    // ---------------------------------------------------------------- HU-14 (CP-HU14-01)

    private static CreateResourceRequest resource(String name, String type) {
        CreateResourceRequest request = new CreateResourceRequest();
        request.setName(name);
        request.setType(type);
        return request;
    }

    /** CP-HU14-01 (camino feliz) - Los tres tipos son válidos sin importar las mayúsculas. */
    @Test
    void losTresTiposDeRecursoSeAceptanSinImportarMayusculas() {
        for (String type : new String[]{"SALA", "equipo", "Personal"}) {
            assertThat(fields(resource("Sala 1", type))).as(type).isEmpty();
        }
    }

    /** CP-HU14-01 - Nombre ausente o en blanco, tipo ausente o fuera de la lista, y nombre de 151 caracteres. */
    @Test
    void elRecursoExigeNombreYTipoValidos() {
        assertThat(fields(resource(null, "SALA"))).containsExactly("name");
        assertThat(fields(resource("   ", "SALA"))).containsExactly("name");
        assertThat(fields(resource(text(151), "SALA"))).containsExactly("name");
        assertThat(fields(resource("Sala 1", null))).containsExactly("type");
        assertThat(fields(resource("Sala 1", "BAÑO"))).containsExactly("type");
    }

    // ---------------------------------------------------------------- HU-18 (CP-HU18-01)

    private static AssignResourcesRequest assign(List<UUID> ids) {
        AssignResourcesRequest request = new AssignResourcesRequest();
        request.setResourceIds(ids);
        return request;
    }

    private static List<UUID> ids(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(UUID.randomUUID());
        }
        return ids;
    }

    /** CP-HU18-01 (camino feliz) - Una lista vacía (quitar todos) y exactamente 100 recursos son válidas. */
    @Test
    void laListaDeRecursosVaciaYElTopeDeCienSeAceptan() {
        assertThat(fields(assign(List.of()))).isEmpty();
        assertThat(fields(assign(ids(100)))).isEmpty();
    }

    /** CP-HU18-01-E3 - Más de 100 recursos se rechaza; también la lista ausente. */
    @Test
    void masDeCienRecursosOLaListaAusenteSeRechaza() {
        assertThat(fields(assign(ids(101)))).containsExactly("resourceIds");
        assertThat(messages(assign(ids(101)))).containsExactly("No se pueden asignar más de 100 recursos a un servicio");
        assertThat(fields(assign(null))).containsExactly("resourceIds");
    }

    // ---------------------------------------------------------------- HU-19 (CP-HU19-01)

    /** CP-HU19-01 (camino feliz) - Rangos HH:mm bien formados, incluido el máximo de 10 por día. */
    @Test
    void losRangosBienFormadosSeAceptanHastaDiez() {
        assertThat(fields(new TimeRange("09:00", "12:00"))).isEmpty();
        List<TimeRange> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add(new TimeRange("0%d:00".formatted(i), "0%d:30".formatted(i)));
        }
        assertThat(fields(new DayRangesRequest(ten))).isEmpty();
        assertThat(fields(new DayRangesRequest(List.of()))).isEmpty();
    }

    /** CP-HU19-01 - Horas con formato inválido, ausentes o más de 10 rangos en un día se rechazan. */
    @Test
    void losRangosMalFormadosOMasDeDiezSeRechazan() {
        assertThat(fields(new TimeRange("24:00", "25:00"))).containsExactlyInAnyOrder("start", "end");
        assertThat(fields(new TimeRange("9:00", "12:00"))).containsExactly("start");
        assertThat(fields(new TimeRange("09:00", null))).containsExactly("end");
        List<TimeRange> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(new TimeRange("08:00", "09:00"));
        }
        assertThat(fields(new DayRangesRequest(eleven))).containsExactly("ranges");
        assertThat(fields(new DayRangesRequest(null))).containsExactly("ranges");
    }

    // ---------------------------------------------------------------- HU-22 (CP-HU22-01)

    private static CreateBookingRequest booking(UUID serviceId, String date, String start, String end) {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setServiceId(serviceId);
        request.setDate(date);
        request.setStartTime(start);
        request.setEndTime(end);
        return request;
    }

    /** CP-HU22-01 (camino feliz) - Una solicitud completa y bien formada es válida (el recurso es opcional). */
    @Test
    void unaSolicitudDeReservaCompletaSeAcepta() {
        assertThat(fields(booking(UUID.randomUUID(), "2026-11-02", "10:00", "11:00"))).isEmpty();
    }

    /** CP-HU22-01 - Faltan servicio, fecha u horas, o las horas no son HH:mm: se señala cada campo. */
    @Test
    void laReservaExigeServicioFechaYHorasBienFormadas() {
        assertThat(fields(booking(null, "2026-11-02", "10:00", "11:00"))).containsExactly("serviceId");
        assertThat(fields(booking(UUID.randomUUID(), null, "10:00", "11:00"))).containsExactly("date");
        assertThat(fields(booking(UUID.randomUUID(), "2026-11-02", null, "11:00"))).containsExactly("startTime");
        assertThat(fields(booking(UUID.randomUUID(), "2026-11-02", "10:00", null))).containsExactly("endTime");
        assertThat(fields(booking(UUID.randomUUID(), "2026-11-02", "9:00", "25:00")))
                .containsExactlyInAnyOrder("startTime", "endTime");
    }

    // ---------------------------------------------------------------- HU-25 y HU-26

    /** CP-HU25-01 (camino feliz) - El motivo del cliente es opcional y admite hasta 500 caracteres. */
    @Test
    void elMotivoDelClienteEsOpcionalYTieneTopeDe500() {
        CancelBookingRequest request = new CancelBookingRequest();
        assertThat(fields(request)).isEmpty();
        request.setReason(text(500));
        assertThat(fields(request)).isEmpty();
        request.setReason(text(501));
        assertThat(fields(request)).containsExactly("reason");
    }

    /** CP-HU26-01-E1 - El proveedor no puede cancelar sin motivo (ausente, vacío o en blanco). */
    @Test
    void elProveedorDebeIndicarUnMotivo() {
        for (String reason : new String[]{null, "", "   "}) {
            ProviderCancelRequest request = new ProviderCancelRequest();
            request.setReason(reason);
            assertThat(fields(request)).as("motivo '%s'", reason).containsExactly("reason");
        }
    }

    /** CP-HU26-01-E2 - Un motivo de más de 500 caracteres se rechaza; 500 es válido. */
    @Test
    void elMotivoDelProveedorTieneTopeDe500() {
        ProviderCancelRequest request = new ProviderCancelRequest();
        request.setReason(text(500));
        assertThat(fields(request)).isEmpty();
        request.setReason(text(501));
        assertThat(fields(request)).containsExactly("reason");
    }
}
