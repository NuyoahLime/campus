package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class UserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository jpa;
    UserRepositoryAdapter(UserJpaRepository r) { this.jpa = r; }
    @Override @Transactional public void save(User u) { jpa.save(UserPersistenceMapper.toEntity(u)); }
    @Override @Transactional(readOnly = true) public Optional<User> findById(UserId id) { return jpa.findById(id.value()).map(UserPersistenceMapper::toDomain); }
    @Override public boolean existsByUsername(String username) { return jpa.existsByUsername(username); }
}
