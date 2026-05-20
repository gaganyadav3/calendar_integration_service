package com.omvrti.calendar_service.persistence.repository;

import com.omvrti.calendar_service.persistence.entity.CustomerUserEntity;
import com.omvrti.calendar_service.persistence.entity.CustomerUserSyncEntity;
import com.omvrti.calendar_service.persistence.entity.SyncVendorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerUserSyncRepository extends JpaRepository<CustomerUserSyncEntity, Long> {

    Optional<CustomerUserSyncEntity> findFirstByCustomerUserAndSyncVendorOrderByIdAsc(
            CustomerUserEntity customerUser, SyncVendorEntity syncVendor);

    default Optional<CustomerUserSyncEntity> findByCustomerUserAndSyncVendor(
            CustomerUserEntity customerUser, SyncVendorEntity syncVendor) {
        return findFirstByCustomerUserAndSyncVendorOrderByIdAsc(customerUser, syncVendor);
    }

    /** ID-based lookup with eager fetch of syncStatus + syncVendor — avoids lazy-load issues after transaction closes. */
    @Query("SELECT s FROM CustomerUserSyncEntity s " +
           "LEFT JOIN FETCH s.syncStatus " +
           "LEFT JOIN FETCH s.syncVendor " +
           "WHERE s.customerUser.id = :userId AND s.syncVendor.id = :vendorId")
    Optional<CustomerUserSyncEntity> findByCustomerUserIdAndSyncVendorId(
            @Param("userId") Long userId,
            @Param("vendorId") Long vendorId);

    List<CustomerUserSyncEntity> findByCustomerUser(CustomerUserEntity customerUser);

    boolean existsByCustomerUserAndSyncVendor(
            CustomerUserEntity customerUser, SyncVendorEntity syncVendor);

    @Query("SELECT s FROM CustomerUserSyncEntity s WHERE s.customerUser = :user " +
           "AND s.syncStatus IS NOT NULL AND s.syncStatus.isActive = 1")
    List<CustomerUserSyncEntity> findActiveByCustomerUser(@Param("user") CustomerUserEntity customerUser);

    @Query("SELECT s FROM CustomerUserSyncEntity s " +
           "JOIN FETCH s.customerUser " +
           "JOIN FETCH s.syncVendor " +
           "JOIN FETCH s.syncStatus " +
           "WHERE s.syncStatus IS NOT NULL AND s.syncStatus.isActive = 1")
    List<CustomerUserSyncEntity> findAllActiveSyncsWithCustomerUser();

    /** Backward-compat — filters in memory; prefer findActiveByCustomerUser for new code. */
    default List<CustomerUserSyncEntity> findByCustomerUserAndIsActiveTrue(CustomerUserEntity customerUser) {
        return findActiveByCustomerUser(customerUser);
    }

    /** Backward-compat */
    default List<CustomerUserSyncEntity> findByIsActiveTrue() {
        return findAllActiveSyncsWithCustomerUser();
    }
}
