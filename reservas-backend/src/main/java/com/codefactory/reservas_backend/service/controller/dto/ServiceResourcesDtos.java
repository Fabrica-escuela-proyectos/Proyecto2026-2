package com.codefactory.reservas_backend.service.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/** Contratos de la asignación de recursos a un servicio (HU-18). */
public final class ServiceResourcesDtos {

    private ServiceResourcesDtos() {
    }

    /**
     * Cuerpo de PUT /api/v1/services/{serviceId}/resources. La lista REEMPLAZA el
     * conjunto actual; vacía = el servicio queda sin recursos. Tope de 100 ids
     * (supuesto del equipo) para no aceptar cuerpos desmesurados.
     */
    @Getter
    @Setter
    public static class AssignResourcesRequest {

        @Schema(description = "Ids de TODOS los recursos que atienden el servicio (reemplaza el conjunto actual; vacío = ninguno)")
        @NotNull(message = "La lista de recursos es obligatoria (puede ir vacía)")
        @Size(max = 100, message = "No se pueden asignar más de 100 recursos a un servicio")
        private List<@NotNull(message = "Los ids de recursos no pueden ser nulos") UUID> resourceIds;
    }

    /** Recurso asignado a un servicio. */
    public record AssignedResource(UUID id, String name, String type, boolean active) {
    }

    /** Recursos que tiene asignados un servicio. */
    public record ServiceResourcesResponse(UUID serviceId, List<AssignedResource> resources) {
    }
}
