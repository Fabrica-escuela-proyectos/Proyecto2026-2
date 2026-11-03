package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.controller.dto.UpdateBusinessRequest;

import java.util.UUID;

/** HU-07 - Editar la información del negocio. Solo el PROVEEDOR dueño. */
public interface BusinessProfileService {

    BusinessProfile update(UUID businessId, UpdateBusinessRequest request, UserIdentity requester, String originIp);
}
