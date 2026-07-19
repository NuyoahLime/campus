package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {
    @Mock UserJpaRepository jpa;
    @InjectMocks UserRepositoryAdapter adapter;

    @Test void saveUpdatesWhenFound() {
        var u = user();
        var existing = entity(u.id().value(), "oldname", "NORMAL");
        existing.setPasswordHash("$2a$12$preservedHash");
        existing.setLoginFailures(5);
        when(jpa.findById(u.id().value())).thenReturn(Optional.of(existing));
        adapter.save(u);
        verify(jpa).saveAndFlush(existing);
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$12$preservedHash");
        assertThat(existing.getLoginFailures()).isEqualTo(5);
    }

    @Test void saveExistingUpdatesDomainFieldsOnly() {
        var u = User.reconstitute(
                new User.Builder().id(new UserId(UUID.randomUUID())).username("newname").platformRole("SUPER_ADMIN"),
                AccountStatus.DISABLED, java.util.List.of());
        var existing = entity(u.id().value(), "oldname", "NORMAL");
        existing.setPasswordHash("$2a$12$hash");
        existing.setLoginFailures(3);
        when(jpa.findById(u.id().value())).thenReturn(Optional.of(existing));
        adapter.save(u);
        assertThat(existing.getUsername()).isEqualTo("newname");
        assertThat(existing.getAccountStatus()).isEqualTo("DISABLED");
        assertThat(existing.getPlatformRole()).isEqualTo("SUPER_ADMIN");
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(existing.getLoginFailures()).isEqualTo(3);
    }

    @Test void saveRejectsNewEntity() {
        var u = user();
        when(jpa.findById(u.id().value())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.save(u))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UserAccountProvisioningPort");
        verify(jpa, never()).saveAndFlush(any());
    }

    @Test void findByIdEmpty() {
        when(jpa.findById(any())).thenReturn(Optional.empty());
        assertThat(adapter.findById(new UserId(UUID.randomUUID()))).isEmpty();
    }

    @Test void restoresNoEvents() {
        var e = ent();
        when(jpa.findById(e.getId())).thenReturn(Optional.of(e));
        assertThat(adapter.findById(new UserId(e.getId())).get().domainEvents()).isEmpty();
    }

    @Test void existsByUsername() {
        adapter.existsByUsername("u");
        verify(jpa).existsByUsername("u");
    }

    private User user() {
        return User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("u"));
    }

    private UserEntity entity(UUID id, String username, String status) {
        var e = new UserEntity();
        e.setId(id);
        e.setUsername(username);
        e.setAccountStatus(status);
        e.setPasswordHash("h");
        return e;
    }

    private UserEntity ent() {
        var e = new UserEntity();
        e.setId(UUID.randomUUID());
        e.setUsername("u");
        e.setAccountStatus("PENDING_ACTIVATION");
        e.setPasswordHash("h");
        return e;
    }
}
