package com.codefactory.reservas_backend.service.infrastructure;

import com.codefactory.reservas_backend.service.domain.ServiceOffering;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    List<ServiceOffering> findByBusinessIdOrderByCreatedAtAsc(UUID businessId);

    List<ServiceOffering> findByBusinessIdAndActiveTrueOrderByNameAsc(UUID businessId);

    /** Bloquea la fila del servicio hasta el fin de la transacción: serializa cambios concurrentes de sus asignaciones. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServiceOffering s where s.id = :id")
    Optional<ServiceOffering> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    /** HU-10: ¿otro servicio del negocio ya usa ese nombre? */
    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);

    /**
     * Bloqueo COMPARTIDO de la fila del servicio (FOR SHARE) leyendo {@code active} directo de la base. Lo toma
     * cada reserva nueva: varias reservas del mismo servicio no se bloquean entre sí, pero sí esperan a una
     * desactivación (HU-11) o edición (HU-10) en curso, que toman el bloqueo exclusivo. Vacío si no existe.
     */
    @Query(value = "select active from services where id = :id for share", nativeQuery = true)
    Optional<Boolean> lockSharedAndReadActive(@Param("id") UUID id);
}
