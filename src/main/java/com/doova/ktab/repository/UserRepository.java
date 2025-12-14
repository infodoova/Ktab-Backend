package com.doova.ktab.respository;

import com.doova.ktab.enums.UserRole;
import com.doova.ktab.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Page<User> findByUserIdContainingAndNameContaining(String id, String fullName, Pageable pageable);
    User findByEmail(String email);
    List<User> findByRole(UserRole userRole);
    boolean existsByEmail(String email);
}
