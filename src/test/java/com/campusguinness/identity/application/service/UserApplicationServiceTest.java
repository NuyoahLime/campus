package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {
    @Mock UserRepository repo;
    UserApplicationService svc;
    @BeforeEach void setUp() { svc = new UserApplicationService(repo); }
    private User user() { return User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("u")); }

    @Nested class Create {
        @Test void success() { assertThat(svc.create("u").status()).isEqualTo("PENDING_ACTIVATION"); verify(repo).save(any()); }
        @Test void rejectsDuplicate() { when(repo.existsByUsername("u")).thenReturn(true); assertThatThrownBy(()->svc.create("u")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
    @Nested class Activate { @Test void success() { var u=user(); when(repo.findById(any())).thenReturn(Optional.of(u)); assertThat(svc.activate(u.id().value()).status()).isEqualTo("NORMAL"); verify(repo).save(any()); } @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.activate(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); } }
    @Nested class Disable { @Test void success() { var u=user(); u.activate(); when(repo.findById(any())).thenReturn(Optional.of(u)); assertThat(svc.disable(u.id().value()).status()).isEqualTo("DISABLED"); verify(repo).save(any()); } }
    @Nested class ReEnable { @Test void success() { var u=user(); u.activate(); u.disable(); when(repo.findById(any())).thenReturn(Optional.of(u)); assertThat(svc.reEnable(u.id().value()).status()).isEqualTo("NORMAL"); verify(repo).save(any()); } }
}
