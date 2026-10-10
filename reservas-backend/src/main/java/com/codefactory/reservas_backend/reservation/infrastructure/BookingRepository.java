package com.codefactory.reservas_backend.reservation.infrastructure;

import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    /** Bloquea la fila de la reserva hasta el fin de la transacción: serializa cancelaciones simultáneas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    Page<Booking> findByClientId(UUID clientId, Pageable pageable);

    Page<Booking> findByClientIdAndStatus(UUID clientId, BookingStatus status, Pageable pageable);

    /** ¿Hay una reserva CONFIRMADA del recurso que se traslape con [start, end)? */
    @Query("""
            select count(b) > 0 from Booking b
            where b.resourceId = :resourceId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt < :end and b.endAt > :start
            """)
    boolean existsConfirmedOverlapping(@Param("resourceId") UUID resourceId,
                                       @Param("start") Instant start, @Param("end") Instant end);

    /** Reservas CONFIRMADAS de esos recursos que tocan [dayStart, dayEnd): lo que HU-20 descuenta. */
    @Query("""
            select b from Booking b
            where b.resourceId in :resourceIds and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt < :dayEnd and b.endAt > :dayStart
            """)
    List<Booking> findConfirmedTouching(@Param("resourceIds") Collection<UUID> resourceIds,
                                        @Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);
}
