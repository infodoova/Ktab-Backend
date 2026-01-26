package com.doova.ktab.repository.user;

import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.model.user.User;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    //    Page<User> findByIdContainingAndNameContaining(String id, String fullName, Pageable pageable);
    Optional<User> findByEmail(String email);

    List<User> findByRole(@NotNull(message = "Role must not be Null") String role);

    boolean existsByEmail(String email);
}
