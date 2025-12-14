package com.doova.doovafeeds.service;

import com.doova.doovafeeds.model.User;
import com.doova.doovafeeds.security.UserPrincipal;
import com.doova.doovafeeds.repository.UserRepository;
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
        User user = userRepo.findByEmail(email);

        if (user == null) {
            // Spring Security will handle this exception and treat it as bad credentials
            throw new UsernameNotFoundException("User not found with email: " + email);
        }

        return new UserPrincipal(user);
    }
}
