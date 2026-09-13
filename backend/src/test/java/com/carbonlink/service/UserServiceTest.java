package com.carbonlink.service;

import com.carbonlink.dto.UserRequestDTO;
import com.carbonlink.dto.UserResponseDTO;
import com.carbonlink.entity.Role;
import com.carbonlink.entity.User;
import com.carbonlink.exception.BadRequestException;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// UserService had no test file at all, yet it owns two of the app's user-facing 400s (bad role
// string, wrong role for the action) and the city -> coordinate resolution every distance
// calculation downstream depends on.
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void createUser_resolvesCoordinatesFromTheCityName() {
        when(userRepository.save(any())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        userService.createUser(new UserRequestDTO("Anjali Nair", "GreenFuel", "BUYER", "Pune", null, null, "Plot 3"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.BUYER);
        assertThat(saved.getCity()).isEqualTo("Pune");
        assertThat(saved.getLocationLat()).isEqualTo(18.520);
        assertThat(saved.getLocationLng()).isEqualTo(73.856);
    }

    @Test
    void createUser_acceptsALowercaseOrPaddedRole() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(new UserRequestDTO("Rakesh", "Ambuja", "  emitter  ", "Mumbai", null, null, null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.EMITTER);
    }

    @Test
    void createUser_400sOnAnUnknownRole() {
        assertThatThrownBy(() -> userService.createUser(
                new UserRequestDTO("Someone", "SomeCo", "ADMIN", "Mumbai", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("role must be EMITTER or BUYER");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_400sOnAnUnsupportedCity() {
        assertThatThrownBy(() -> userService.createUser(
                new UserRequestDTO("Someone", "SomeCo", "BUYER", "Atlantis", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("city");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_neverExposesRawCoordinates_onlyTheCityName() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponseDTO response = userService.createUser(
                new UserRequestDTO("Anjali", "GreenFuel", "BUYER", "Pune", null, null, null));

        assertThat(response.city()).isEqualTo("Pune");
        assertThat(UserResponseDTO.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("locationLat", "locationLng");
    }

    // --- Sign-in ---

    @Test
    void login_withCorrectCredentials_returnsTheAccount() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        userService.createUser(new UserRequestDTO("Rakesh", "Ambuja", "EMITTER", "Mumbai", "ambuja", "123", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User stored = captor.getValue();
        when(userRepository.findByUsernameIgnoreCase("ambuja")).thenReturn(Optional.of(stored));

        assertThat(userService.login("ambuja", "123").companyName()).isEqualTo("Ambuja");
    }

    @Test
    void createUser_storesTheHashNotThePassword() {
        // A plaintext password column is the kind of thing that survives into something real.
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(new UserRequestDTO("Rakesh", "Ambuja", "EMITTER", "Mumbai", "ambuja", "123", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotNull().isNotEqualTo("123").startsWith("$2");
    }

    @Test
    void login_isCaseInsensitiveOnTheUsername() {
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        userService.createUser(new UserRequestDTO("Rakesh", "Ambuja", "EMITTER", "Mumbai", "Ambuja", "123", null));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // Stored lowercased...
        assertThat(captor.getValue().getUsername()).isEqualTo("ambuja");

        when(userRepository.findByUsernameIgnoreCase("ambuja")).thenReturn(Optional.of(captor.getValue()));
        // ...and looked up the same way regardless of how it was typed.
        assertThat(userService.login("AMBUJA", "123")).isNotNull();
    }

    @Test
    void login_wrongPassword_andUnknownUsername_failIdentically() {
        User stored = user(1L, Role.EMITTER);
        stored.setUsername("ambuja");
        stored.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("123"));
        when(userRepository.findByUsernameIgnoreCase("ambuja")).thenReturn(Optional.of(stored));
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        // Identical messages on purpose: a different one for each case tells an attacker which
        // usernames exist.
        assertThatThrownBy(() -> userService.login("ambuja", "nope"))
                .isInstanceOf(BadRequestException.class).hasMessage("Incorrect username or password");
        assertThatThrownBy(() -> userService.login("ghost", "123"))
                .isInstanceOf(BadRequestException.class).hasMessage("Incorrect username or password");
    }

    @Test
    void login_accountWithNoPasswordSet_cannotBeSignedInto() {
        User passwordless = user(1L, Role.BUYER);
        passwordless.setUsername("legacy");
        when(userRepository.findByUsernameIgnoreCase("legacy")).thenReturn(Optional.of(passwordless));

        assertThatThrownBy(() -> userService.login("legacy", ""))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createUser_duplicateUsername_is400() {
        when(userRepository.existsByUsernameIgnoreCase("ambuja")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                new UserRequestDTO("Someone", "SomeCo", "EMITTER", "Mumbai", "Ambuja", "123", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already taken");
        verify(userRepository, never()).save(any());
    }

    @Test
    void userResponse_neverCarriesThePasswordHash() {
        assertThat(UserResponseDTO.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .contains("username")
                .doesNotContain("passwordHash", "password");
    }

    @Test
    void getUsers_withoutARole_returnsEveryone() {
        when(userRepository.findAll()).thenReturn(List.of(user(1L, Role.BUYER), user(2L, Role.EMITTER)));

        assertThat(userService.getUsers(null)).extracting(UserResponseDTO::id).containsExactly(1L, 2L);
    }

    @Test
    void getUsers_withARole_filtersByIt() {
        when(userRepository.findByRole(Role.EMITTER)).thenReturn(List.of(user(2L, Role.EMITTER)));

        assertThat(userService.getUsers(Role.EMITTER)).extracting(UserResponseDTO::id).containsExactly(2L);
    }

    @Test
    void getUser_notFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireUserWithRole_wrongRole_throwsBadRequest() {
        // Guards "an emitter listed this" / "a buyer requested that" - without it a buyer could
        // create listings and the whole two-sided model would leak.
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.BUYER)));

        assertThatThrownBy(() -> userService.requireUserWithRole(1L, Role.EMITTER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expected EMITTER");
    }

    @Test
    void requireUserWithRole_matchingRole_returnsTheUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.EMITTER)));

        assertThat(userService.requireUserWithRole(1L, Role.EMITTER).getId()).isEqualTo(1L);
    }

    private static User user(Long id, Role role) {
        return User.builder()
                .id(id).name("Rep").companyName("Company " + id).role(role)
                .city("Mumbai").locationLat(19.076).locationLng(72.877)
                .build();
    }
}
