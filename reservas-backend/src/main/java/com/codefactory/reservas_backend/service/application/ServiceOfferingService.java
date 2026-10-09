package com.codefactory.reservas_backend.service.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.service.controller.dto.CreateServiceRequest;
import com.codefactory.reservas_backend.service.controller.dto.ServiceResponse;

import java.util.List;
import java.util.UUID;

/** Casos de uso del catálogo de servicios de un negocio (HU-09; HU-10..13 se apoyan aquí). */
public interface ServiceOfferingService {

    ServiceResponse create(UUID businessId, CreateServiceRequest request, UserIdentity requester, String originIp);

    List<ServiceResponse> list(UUID businessId, UserIdentity requester);
}
