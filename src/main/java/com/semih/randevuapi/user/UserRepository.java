package com.semih.randevuapi.user;

import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** The logged-in user behind a token; the account may have been deleted since the token was issued. */
    default User getByEmail(String email) {
        return findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User no longer exists"));
    }
}
