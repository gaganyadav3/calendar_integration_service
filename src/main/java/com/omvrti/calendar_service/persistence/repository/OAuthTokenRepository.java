package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.persistence.entity.OAuthTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthTokenEntity, Long> {

    Optional<OAuthTokenEntity> findByUserEmailAndProvider(String userEmail, ProviderType provider);

    void deleteByUserEmailAndProvider(String userEmail, ProviderType provider);
}

