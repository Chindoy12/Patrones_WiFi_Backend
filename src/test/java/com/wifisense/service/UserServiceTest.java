package com.wifisense.service;

import com.wifisense.dto.CreateUserRequest;
import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import com.wifisense.security.AccountProperties;
import com.wifisense.security.ContactPolicy;
import com.wifisense.security.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(users, encoder, new PasswordPolicy(),
            new AccountProperties("admin", "Admin#2026a", 3), new ContactPolicy());

    @Test
    void createsUserWithHashedPassword() {
        when(encoder.encode("Redes#2026a")).thenReturn("$2a$hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(new CreateUserRequest("jaider", "Redes#2026a", User.Role.ANALYST));

        assertThat(created.username()).isEqualTo("jaider");
        verify(users).save(argThat(user -> user.getPasswordHash().equals("$2a$hash")));
    }

    @Test
    void rejectsWeakPasswordWithAllReasons() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest("jaider", "weak", User.Role.VIEWER)))
                .isInstanceOfSatisfying(InvalidAccountException.class, e -> assertThat(e.getErrors()).hasSize(4));
        verify(users, never()).save(any());
    }

    @Test
    void neverExceedsTheMaximumNumberOfUsers() {
        when(users.countByRoleIn(User.Role.STAFF)).thenReturn(3L);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("cuarto", "Redes#2026a", User.Role.VIEWER)))
                .isInstanceOf(UserLimitException.class)
                .hasMessageContaining("máximo de 3 usuarios");
    }

    @Test
    void adminCannotDeleteOrDemoteThemselves() {
        User admin = new User("admin", "admin@wifisense.local", "hash", User.Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 1L);
        when(users.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.delete(1L, "admin")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.changeRole(1L, User.Role.VIEWER, "admin")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void endUsersRegisterWithTheUserRole() {
        when(encoder.encode("Redes#2026a")).thenReturn("$2a$hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var registered = service.register("  ana.lopez@gmail.com ", "Redes#2026a", null, null);

        assertThat(registered.role()).isEqualTo(User.Role.USER);
        assertThat(registered.username()).isEqualTo("ana.lopez@gmail.com");
        verify(users).save(argThat(user -> user.getEmail().equals("ana.lopez@gmail.com")));
    }

    @Test
    void endUsersDoNotCountTowardsTheTeamLimit() {
        when(users.countByRoleIn(User.Role.STAFF)).thenReturn(3L);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.register("ana", "Redes#2026a", "ana@gmail.com", null).role()).isEqualTo(User.Role.USER);
    }

    @Test
    void registrationRejectsWeakPasswordsDuplicatesAndReservedNames() {
        assertThatThrownBy(() -> service.register("ana", "weak", "ana@gmail.com", null)).isInstanceOf(InvalidAccountException.class);

        when(users.existsByUsernameIgnoreCase("Ana")).thenReturn(true);
        assertThatThrownBy(() -> service.register("Ana", "Redes#2026a", "ana@gmail.com", null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Ese nombre de usuario no está disponible.");

        assertThatThrownBy(() -> service.register("Admin", "Redes#2026a", "admin@gmail.com", null)).isInstanceOf(DuplicateResourceException.class);
        verify(users, never()).save(any());
    }

    @Test
    void registrationKeepsARecoveryEmailAndAnInternationalPhone() {
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register("ana", "Redes#2026a", " Ana.Lopez@Gmail.com ", "300 123 4567");

        verify(users).save(argThat(user -> user.getEmail().equals("ana.lopez@gmail.com")
                && user.getPhone().equals("+573001234567") && user.hasRealEmail()));
    }

    @Test
    void registrationAsksForTheEmailAndReportsEveryProblemAtOnce() {
        assertThatThrownBy(() -> service.register("ana", "Redes#2026a", null, null))
                .isInstanceOfSatisfying(InvalidAccountException.class, e -> assertThat(e.getErrors())
                        .containsExactly("Escribe tu correo: lo usamos solo para que puedas recuperar tu contraseña."));
        assertThatThrownBy(() -> service.register("ana", "weak", "no-es-correo", "12"))
                .isInstanceOfSatisfying(InvalidAccountException.class, e -> assertThat(e.getErrors())
                        .contains(ContactPolicy.EMAIL_ERROR, ContactPolicy.PHONE_ERROR).hasSize(6));
        verify(users, never()).save(any());
    }

    @Test
    void anEmailBelongsToOneAccountOnly() {
        when(users.findByEmailIgnoreCase("ana@gmail.com"))
                .thenReturn(Optional.of(new User("otra", "ana@gmail.com", "hash", User.Role.USER)));

        assertThatThrownBy(() -> service.register("ana", "Redes#2026a", "ANA@gmail.com", null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Ese correo ya tiene una cuenta. Si es tuyo, recupera la contraseña.");
    }

    @Test
    void administratorOnlyManagesTeamAccounts() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest("ana", "Redes#2026a", User.Role.USER)))
                .isInstanceOf(IllegalArgumentException.class);

        User endUser = new User("ana", "ana@wifisense.local", "hash", User.Role.USER);
        ReflectionTestUtils.setField(endUser, "id", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(endUser));
        assertThatThrownBy(() -> service.delete(7L, "admin")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.changeRole(7L, User.Role.USER, "admin")).isInstanceOf(IllegalArgumentException.class);
    }
}
