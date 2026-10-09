package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.controller.dto.CreateResourceRequest;
import com.codefactory.reservas_backend.resource.controller.dto.ResourceResponse;

import java.util.List;
import java.util.UUID;

/** Casos de uso de los recursos de un negocio (HU-14; HU-15..19 se apoyan aquí). */
public interface ResourceService {

    ResourceResponse create(UUID businessId, CreateResourceRequest request, UserIdentity requester, String originIp);

    List<ResourceResponse> list(UUID businessId, UserIdentity requester);
}
