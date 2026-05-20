package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CustomerUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerUserRepository extends JpaRepository<CustomerUserEntity, Long> {

    /**
     * Returns the oldest (lowest ID) user row matching the email.
     * Spring Data limits to 1 row — prevents NonUniqueResultException when
     * duplicate CUSTOMER_USER rows exist for the same email (legacy data issue).
     */
    Optional<CustomerUserEntity> findFirstByEmailOrderByIdAsc(String email);

    boolean existsByEmail(String email);

    /** All callers use this — delegates to findFirst to stay safe with duplicate rows. */
    default Optional<CustomerUserEntity> findByEmail(String email) {
        return findFirstByEmailOrderByIdAsc(email);
    }
}
