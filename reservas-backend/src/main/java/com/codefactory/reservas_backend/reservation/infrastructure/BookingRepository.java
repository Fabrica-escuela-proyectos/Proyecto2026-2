package com.codefactory.reservas_backend.reservation.infrastructure;

import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("""
            select count(b) from Booking b
            where b.resourceId = :resourceId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt > :now
            """)
    long countFutureConfirmedOfResource(@Param("resourceId") UUID resourceId, @Param("now") Instant now);

    /** Cancela de una vez las reservas CONFIRMADAS futuras de un recurso; devuelve cuántas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA,
                   b.cancelOrigin = :origin, b.cancelReason = :reason, b.cancelledAt = :now
            where b.resourceId = :resourceId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt > :now
            """)
    int cancelFutureConfirmedOfResource(@Param("resourceId") UUID resourceId, @Param("origin") CancelOrigin origin,
                                        @Param("reason") String reason, @Param("now") Instant now);

    /** Cancela de una vez las reservas CONFIRMADAS futuras de un cliente (HU-28); devuelve cuántas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA,
                   b.cancelOrigin = :origin, b.cancelReason = :reason, b.cancelledAt = :now
            where b.clientId = :clientId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt > :now
            """)
    int cancelFutureConfirmedOfClient(@Param("clientId") UUID clientId, @Param("origin") CancelOrigin origin,
                                      @Param("reason") String reason, @Param("now") Instant now);

    /** Cancela de una vez las reservas CONFIRMADAS futuras de unos negocios (HU-28); devuelve cuántas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA,
                   b.cancelOrigin = :origin, b.cancelReason = :reason, b.cancelledAt = :now
            where b.businessId in :businessIds and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt > :now
            """)
    int cancelFutureConfirmedOfBusinesses(@Param("businessIds") Collection<UUID> businessIds,
                                          @Param("origin") CancelOrigin origin, @Param("reason") String reason,
                                          @Param("now") Instant now);

    @Query("""
            select count(b) from Booking b
            where b.serviceId = :serviceId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt > :now
            """)
    long countFutureConfirmedOfService(@Param("serviceId") UUID serviceId, @Param("now") Instant now);

    /** HU-11: cancela de una vez las reservas CONFIRMADAS futuras de un servicio; devuelve cuántas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA,
                   b.cancelOrigin = :origin, b.cancelReason = :reason, b.cancelledAt = :now
            where b.serviceId = :serviceId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt > :now
            """)
    int cancelFutureConfirmedOfService(@Param("serviceId") UUID serviceId, @Param("origin") CancelOrigin origin,
                                       @Param("reason") String reason, @Param("now") Instant now);

    /** HU-21: reservas CONFIRMADAS del recurso, aún no iniciadas, que se traslapan con [start, end). */
    @Query("""
            select count(b) from Booking b
            where b.resourceId = :resourceId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt > :now and b.startAt < :end and b.endAt > :start
            """)
    long countFutureConfirmedOfResourceIn(@Param("resourceId") UUID resourceId, @Param("start") Instant start,
                                          @Param("end") Instant end, @Param("now") Instant now);

    /** HU-21: cancela las reservas CONFIRMADAS del recurso, aún no iniciadas, que se traslapan con [start, end). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Booking b set b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA,
                   b.cancelOrigin = :origin, b.cancelReason = :reason, b.cancelledAt = :now
            where b.resourceId = :resourceId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CONFIRMADA
              and b.startAt > :now and b.startAt < :end and b.endAt > :start
            """)
    int cancelFutureConfirmedOfResourceIn(@Param("resourceId") UUID resourceId, @Param("start") Instant start,
                                          @Param("end") Instant end, @Param("origin") CancelOrigin origin,
                                          @Param("reason") String reason, @Param("now") Instant now);

    // --- Reportes del Sprint 3 (HU-29..34): agregados por negocio o de toda la plataforma, periodo [from, to) sobre start_at ---

    @Query("select b.status, count(b) from Booking b where b.businessId = :businessId and b.startAt >= :from and b.startAt < :to group by b.status")
    List<Object[]> countByStatusInBusiness(@Param("businessId") UUID businessId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select b.status, count(b) from Booking b where b.startAt >= :from and b.startAt < :to group by b.status")
    List<Object[]> countByStatusInPlatform(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select b.cancelOrigin, count(b) from Booking b
            where b.businessId = :businessId and b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA
              and b.startAt >= :from and b.startAt < :to
            group by b.cancelOrigin
            """)
    List<Object[]> cancelledByOriginInBusiness(@Param("businessId") UUID businessId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select b.cancelOrigin, count(b) from Booking b
            where b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA
              and b.startAt >= :from and b.startAt < :to
            group by b.cancelOrigin
            """)
    List<Object[]> cancelledByOriginInPlatform(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select b.status, sum(b.priceCop) from Booking b where b.businessId = :businessId and b.startAt >= :from and b.startAt < :to group by b.status")
    List<Object[]> valueByStatusInBusiness(@Param("businessId") UUID businessId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select b.status, sum(b.priceCop) from Booking b where b.startAt >= :from and b.startAt < :to group by b.status")
    List<Object[]> valueByStatusInPlatform(@Param("from") Instant from, @Param("to") Instant to);

    /** Por servicio: [serviceId, nombre (copia), reservadas = confirmadas + completadas, canceladas]. */
    @Query("""
            select b.serviceId, max(b.serviceName),
                   sum(case when b.status <> com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA then 1 else 0 end),
                   sum(case when b.status = com.codefactory.reservas_backend.reservation.domain.BookingStatus.CANCELADA then 1 else 0 end)
            from Booking b
            where b.businessId = :businessId and b.startAt >= :from and b.startAt < :to
            group by b.serviceId
            """)
    List<Object[]> countByServiceInBusiness(@Param("businessId") UUID businessId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Minutos ocupados por recurso y día de Bogotá (confirmadas + completadas con recurso): [resource_id, día, minutos].
     * La base agrupa y suma: a la aplicación solo llega una fila por recurso y día, no cada reserva.
     */
    @Query(value = OCCUPIED_SELECT + " and b.business_id = :businessId" + OCCUPIED_GROUP, nativeQuery = true)
    List<Object[]> occupiedMinutesInBusiness(@Param("businessId") UUID businessId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = OCCUPIED_SELECT + OCCUPIED_GROUP, nativeQuery = true)
    List<Object[]> occupiedMinutesInPlatform(@Param("from") Instant from, @Param("to") Instant to);

    String OCCUPIED_SELECT = "select b.resource_id, cast(b.start_at at time zone 'America/Bogota' as date) as day,"
            + " cast(sum(extract(epoch from (b.end_at - b.start_at))) / 60 as bigint) as minutes"
            + " from bookings b where b.resource_id is not null and b.status <> 'CANCELADA'"
            + " and b.start_at >= :from and b.start_at < :to";
    String OCCUPIED_GROUP = " group by b.resource_id, day";

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
