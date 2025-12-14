package com.doova.doovafeeds.service;

import com.doova.doovafeeds.enums.ErrorMessage;
import com.doova.doovafeeds.exceptions.BadRequestException;
import com.doova.doovafeeds.model.User;
import com.doova.doovafeeds.repository.UserRepository;
import com.doova.doovafeeds.api.dto.UserLoginRequest;
import com.doova.doovafeeds.api.dto.UserRegisterRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final BCryptPasswordEncoder encoder;


    @Transactional
    public User register(UserRegisterRequest request) {
            // basic uniqueness check
            String email = String.valueOf(request.email());
            log.debug("Register request email='{}' (class={})", email, email == null ? "null" : email.getClass().getName());
            try {
                if (userRepository.existsByEmail(email)) {
                    throw new BadRequestException(ErrorMessage.USERID_ALREADY_EXISTS.getMessage(messageSource));
                }
            } catch (Exception ex) {
                // Log detailed info if repository check fails
                log.error("existsByEmail threw an exception for email='{}'. Exception: {}", email, ex.toString(), ex);
                throw ex;
            }

            User user = new User();
            user.setEmail(email);
            user.setFirstName(request.firstName());
            user.setMiddleName(request.middleName());
            user.setLastName(request.lastName());
            user.setActive(true);
            user.setPasswordAndDigest(request.password(), encoder);

            log.debug("Saving user object (class={}) email={}", user.getClass().getName(), user.getEmail());
            try {
                return userRepository.save(user);
            } catch (Exception ex) {
                log.error("userRepository.save threw an exception for user email='{}'. Exception: {}", user.getEmail(), ex.toString(), ex);
                throw ex;
            }
    }


    public String verify(UserLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return jwtService.generateToken(userDetails);
    }



}
