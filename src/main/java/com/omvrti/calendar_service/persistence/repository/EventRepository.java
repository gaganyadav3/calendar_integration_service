package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.common.enums.EventSource;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.EventEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {
    
    Optional<EventEntity> findByInternalId(String internalId);
    List<EventEntity> findByExternalIdAndProvider(String externalId, ProviderType provider);

    List<EventEntity> findByUser(UserEntity user);
    List<EventEntity> findByUserAndIsDeletedFalse(UserEntity user);
    List<EventEntity> findByUserAndProviderAndIsDeletedFalse(UserEntity user, ProviderType provider);
    
    Page<EventEntity> findByUserAndIsDeletedFalseOrderByStartTimeDesc(UserEntity user, Pageable pageable);
    
    List<EventEntity> findByUserAndStartTimeBetweenAndIsDeletedFalse(
        UserEntity user, 
        OffsetDateTime start, 
        OffsetDateTime end
    );
    
    List<EventEntity> findByUserAndProviderAndUpdatedAtAfterAndIsDeletedFalse(
        UserEntity user, 
        ProviderType provider, 
        OffsetDateTime since
    );

    List<EventEntity> findByUserAndProviderAndUpdatedAtAfter(
        UserEntity user,
        ProviderType provider,
        OffsetDateTime since
    );
    
    @Query("SELECT e FROM EventEntity e WHERE e.user = :user AND e.isDeleted = false AND e.isCancelled = false AND e.endTime > CURRENT_TIMESTAMP")
    List<EventEntity> findUpcomingBookings(@Param("user") UserEntity user);
    
    @Query("SELECT COUNT(e) FROM EventEntity e WHERE e.user = :user AND e.provider = :provider AND e.isDeleted = false")
    long countEventsByUserAndProvider(@Param("user") UserEntity user, @Param("provider") ProviderType provider);
    
    @Query("SELECT COUNT(e) FROM EventEntity e WHERE e.user = :user AND e.provider = :provider AND e.isDeleted = false AND e.isCancelled = false AND e.endTime > CURRENT_TIMESTAMP")
    long countUpcomingBookingsByUserAndProvider(@Param("user") UserEntity user, @Param("provider") ProviderType provider);
    
    @Query("SELECT COUNT(DISTINCT e.provider) FROM EventEntity e WHERE e.user = :user AND e.isDeleted = false")
    long countProviders(@Param("user") UserEntity user);
    
    void deleteByUserAndProvider(UserEntity user, ProviderType provider);
}
