package com.carbonlink.controller;

import com.carbonlink.dto.LoginRequestDTO;
import com.carbonlink.dto.UserResponseDTO;
import com.carbonlink.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Username + password sign-in for the seeded demo accounts. There are no sessions or tokens
// here: the app has always identified the current user by the record the client holds, and
// this returns that same record. It's an authentication convenience for the demo, not an
// authorization layer - every other endpoint remains open exactly as before.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public UserResponseDTO login(@Valid @RequestBody LoginRequestDTO dto) {
        return userService.login(dto.username(), dto.password());
    }
}
