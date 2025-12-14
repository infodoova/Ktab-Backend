package com.doova.ktab.service.auth;

import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.security.model.UserPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;

    public MyUserDetailsService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        return userRepo.findByEmail(email).map(UserPrincipal::new).orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }
}
