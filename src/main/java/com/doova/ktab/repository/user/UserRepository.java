package com.doova.ktab.repository.user;

import com.doova.ktab.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(String role);

    Page<User> findByRole(String role, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
        SELECT u FROM User u
        WHERE u.role = :role
          AND (
              LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
          )
    """)
    Page<User> searchByRole(
            @org.springframework.data.repository.query.Param("role") String role,
            @org.springframework.data.repository.query.Param("search") String search,
            Pageable pageable
    );

    boolean existsByEmail(String email);

    List<User> findAllByLibraryOrganizationId(Long libraryOrgId);

    long countByLibraryOrganizationId(Long libraryOrgId);
}
