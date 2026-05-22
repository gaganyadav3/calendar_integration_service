package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CustomerUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerUserRepository extends JpaRepository<CustomerUserEntity, Long> {

    Optional<CustomerUserEntity> findFirstByEmailOrderByIdAsc(String email);

    boolean existsByEmail(String email);

    /**
     * Native SQL: bypasses JPQL processing entirely.
     * LOWER+TRIM on both sides handles whitespace padding and case differences.
     * ROWNUM = 1 limits to one row (safe even if duplicates exist).
     */
    @Query(value = "SELECT * FROM CUSTOMER_USER WHERE LOWER(TRIM(EMAIL)) = LOWER(TRIM(:email)) AND ROWNUM = 1",
           nativeQuery = true)
    Optional<CustomerUserEntity> findByEmailNative(@Param("email") String email);

    default Optional<CustomerUserEntity> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String trimmed = email.trim();
        Optional<CustomerUserEntity> result = findFirstByEmailOrderByIdAsc(trimmed);
        if (result.isPresent()) return result;
        return findByEmailNative(trimmed);
    }
}
