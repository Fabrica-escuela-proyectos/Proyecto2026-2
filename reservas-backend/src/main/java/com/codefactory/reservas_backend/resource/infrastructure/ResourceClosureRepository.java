package com.codefactory.reservas_backend.resource.infrastructure;

import com.codefactory.reservas_backend.resource.domain.ResourceClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResourceClosureRepository extends JpaRepository<ResourceClosure, UUID> {

    /** Cierres que aún no terminan, del más próximo al más lejano (lo que ve el proveedor). */
    List<ResourceClosure> findByResourceIdAndEndAtAfterOrderByStartAtAsc(UUID resourceId, Instant now);

    @Query("""
            select count(c) > 0 from ResourceClosure c
            where c.resourceId = :resourceId and c.startAt < :end and c.endAt > :start
            """)
    boolean existsOverlapping(@Param("resourceId") UUID resourceId, @Param("start") Instant start, @Param("end") Instant end);

    /** Cierres de esos recursos que tocan [from, to): lo que se descuenta del horario (HU-20, HU-22, reportes). */
    @Query("""
            select c from ResourceClosure c
            where c.resourceId in :resourceIds and c.startAt < :to and c.endAt > :from
            order by c.startAt
            """)
    List<ResourceClosure> findTouching(@Param("resourceIds") Collection<UUID> resourceIds,
                                       @Param("from") Instant from, @Param("to") Instant to);
}
