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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {
    @Mock UserJpaRepository jpa;
    @InjectMocks UserRepositoryAdapter adapter;
    @Test void save() { adapter.save(user()); verify(jpa).save(any()); }
    @Test void findByIdEmpty() { when(jpa.findById(any())).thenReturn(Optional.empty()); assertThat(adapter.findById(new UserId(UUID.randomUUID()))).isEmpty(); }
    @Test void restoresNoEvents() { var e=ent(); when(jpa.findById(e.getId())).thenReturn(Optional.of(e)); assertThat(adapter.findById(new UserId(e.getId())).get().domainEvents()).isEmpty(); }
    @Test void existsByUsername() { adapter.existsByUsername("u"); verify(jpa).existsByUsername("u"); }
    private User user() { return User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("u")); }
    private UserEntity ent() { var e=new UserEntity(); e.setId(UUID.randomUUID()); e.setUsername("u"); e.setAccountStatus("PENDING_ACTIVATION"); return e; }
}
