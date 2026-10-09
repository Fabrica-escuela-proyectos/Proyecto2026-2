package com.codefactory.reservas_backend.resource.infrastructure;

import com.codefactory.reservas_backend.resource.domain.ResourceAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ResourceAvailabilityRepository extends JpaRepository<ResourceAvailability, UUID> {

    List<ResourceAvailability> findByResourceIdOrderByDayOfWeekAscStartTimeAsc(UUID resourceId);

    List<ResourceAvailability> findByResourceIdInAndDayOfWeek(java.util.Collection<UUID> resourceIds, int dayOfWeek);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResourceAvailability a where a.resourceId = :resourceId")
    void deleteAllOfResource(@Param("resourceId") UUID resourceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResourceAvailability a where a.resourceId = :resourceId and a.dayOfWeek = :day")
    void deleteDayOfResource(@Param("resourceId") UUID resourceId, @Param("day") int day);
}
