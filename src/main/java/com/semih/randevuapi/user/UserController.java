package com.semih.randevuapi.user;

import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "The currently logged-in user, resolved from the access token")
    public UserResponse me(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User no longer exists"));
    }
}
