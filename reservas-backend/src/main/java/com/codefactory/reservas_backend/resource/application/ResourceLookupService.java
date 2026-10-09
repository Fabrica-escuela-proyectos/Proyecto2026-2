package com.codefactory.reservas_backend.resource.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Consulta de recursos para otros módulos (HU-18 asigna recursos a servicios;
 * HU-20 y HU-22 los usarán). Así Service no importa la entidad ni el
 * repositorio de Resource (ADR-003).
 */
public interface ResourceLookupService {

    /** De los ids pedidos, devuelve solo los que existen Y pertenecen al negocio indicado. */
    List<ResourceInfo> findInBusiness(UUID businessId, Collection<UUID> resourceIds);

    /** Recursos existentes con esos ids (sin filtrar por negocio), ordenados por nombre. */
    List<ResourceInfo> findByIds(Collection<UUID> resourceIds);
}
