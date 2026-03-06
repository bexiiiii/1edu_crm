package com.ondeedu.staff.repository;

import com.ondeedu.staff.entity.Staff;
import com.ondeedu.staff.entity.StaffRole;
import com.ondeedu.staff.entity.StaffStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {

    Page<Staff> findByRole(StaffRole role, Pageable pageable);

    Page<Staff> findByStatus(StaffStatus status, Pageable pageable);

    Optional<Staff> findByEmail(String email);

    Optional<Staff> findByPhone(String phone);

    @Query("""
        SELECT s FROM Staff s
        WHERE LOWER(s.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR s.phone LIKE CONCAT('%', :query, '%')
           OR LOWER(s.email) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Staff> search(@Param("query") String query, Pageable pageable);

    long countByStatus(StaffStatus status);

    long countByRole(StaffRole role);
}
