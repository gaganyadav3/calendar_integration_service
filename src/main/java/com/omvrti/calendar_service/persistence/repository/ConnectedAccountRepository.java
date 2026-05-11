package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.ConnectedAccountEntity;
import com.omvrti.calendar_service.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccountEntity, Long> {
    Optional<ConnectedAccountEntity> findByUserAndProvider(UserEntity user, ProviderType provider);
    List<ConnectedAccountEntity> findByUser(UserEntity user);
    List<ConnectedAccountEntity> findByUserAndIsActiveTrue(UserEntity user);
    List<ConnectedAccountEntity> findByIsActiveTrue();
    boolean existsByUserAndProvider(UserEntity user, ProviderType provider);

    @Query("SELECT ca FROM ConnectedAccountEntity ca JOIN FETCH ca.user WHERE ca.isActive = true")
    List<ConnectedAccountEntity> findByIsActiveTrueWithUser();
}

