package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResourcesDtos.ServiceResourcesResponse;

import java.util.List;
import java.util.UUID;

/**
 * HU-18 - Asignar recursos a un servicio ("dónde se presta"). Es requisito de la
 * disponibilidad (HU-20) y de las reservas (HU-22).
 */
public interface ServiceResourceAssignmentService {

    /**
     * Reemplaza el conjunto de recursos del servicio (idempotente y atómico: si
     * algún recurso no existe o es de otro negocio no se cambia nada).
     *
     * @throws com.codefactory.reservas_backend.service.domain.ServiceNotFoundException si el servicio no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException si el negocio no es del solicitante (403)
     * @throws com.codefactory.reservas_backend.service.domain.InvalidResourceAssignmentException recurso inexistente o ajeno (400)
     */
    ServiceResourcesResponse replaceResources(UUID serviceId, List<UUID> resourceIds, UserIdentity requester, String originIp);

    ServiceResourcesResponse getResources(UUID serviceId, UserIdentity requester);
}
