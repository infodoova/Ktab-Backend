package com.doova.ktab.repository.library;

import com.doova.ktab.model.library.LibraryOrganization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LibraryOrganizationRepository extends JpaRepository<LibraryOrganization, Long>, JpaSpecificationExecutor<LibraryOrganization> {

    Optional<LibraryOrganization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    Page<LibraryOrganization> findAllByStatus(String status, Pageable pageable);

    Page<LibraryOrganization> findByNameContainingIgnoreCaseAndStatus(String name, String status, Pageable pageable);
}
