package com.doova.ktab.service.auth;

import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepo.findByEmail(email).map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }
}
