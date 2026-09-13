package com.carbonlink.service;

import com.carbonlink.constants.CityCoordinates;
import com.carbonlink.dto.UserRequestDTO;
import com.carbonlink.dto.UserResponseDTO;
import com.carbonlink.entity.Role;
import com.carbonlink.entity.User;
import com.carbonlink.exception.BadRequestException;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponseDTO createUser(UserRequestDTO dto) {
        Role role = parseRole(dto.role());
        CityCoordinates.LatLng coordinates = CityCoordinates.lookup(dto.city())
                .orElseThrow(() -> new BadRequestException("city must be one of the supported cities"));

        String username = normalizeUsername(dto.username());
        if (username != null && userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("username '" + username + "' is already taken");
        }

        User user = User.builder()
                .name(dto.name())
                .companyName(dto.companyName())
                .role(role)
                .city(dto.city())
                .address(dto.address())
                .username(username)
                .passwordHash(hashOrNull(dto.password()))
                .locationLat(coordinates.lat())
                .locationLng(coordinates.lng())
                .build();

        return toResponseDto(userRepository.save(user));
    }

    public UserResponseDTO getUser(Long id) {
        return toResponseDto(requireUser(id));
    }

    public List<UserResponseDTO> getUsers(Role role) {
        List<User> users = role != null ? userRepository.findByRole(role) : userRepository.findAll();
        return users.stream().map(this::toResponseDto).toList();
    }

    // Username + password sign-in, sitting alongside the one-click profile list rather than
    // replacing it. Both failure modes return the SAME message on purpose: saying which half
    // was wrong tells an attacker which usernames exist.
    public UserResponseDTO login(String username, String rawPassword) {
        String normalized = normalizeUsername(username);
        User user = normalized == null ? null
                : userRepository.findByUsernameIgnoreCase(normalized).orElse(null);

        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadRequestException("Incorrect username or password");
        }
        return toResponseDto(user);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim().toLowerCase();
    }

    private String hashOrNull(String rawPassword) {
        return rawPassword == null || rawPassword.isBlank() ? null : passwordEncoder.encode(rawPassword);
    }

    User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    User requireUserWithRole(Long id, Role expectedRole) {
        User user = requireUser(id);
        if (user.getRole() != expectedRole) {
            throw new BadRequestException(
                    "User " + id + " has role " + user.getRole() + ", expected " + expectedRole);
        }
        return user;
    }

    private Role parseRole(String rawRole) {
        try {
            return Role.valueOf(rawRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("role must be EMITTER or BUYER");
        }
    }

    private UserResponseDTO toResponseDto(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getCompanyName(),
                user.getUsername(),
                user.getRole(),
                user.getCity(),
                user.getAddress(),
                user.getCreatedAt());
    }
}
