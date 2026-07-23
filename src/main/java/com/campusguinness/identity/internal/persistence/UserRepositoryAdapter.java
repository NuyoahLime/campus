package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class UserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository jpa;
    UserRepositoryAdapter(UserJpaRepository r) { this.jpa = r; }

    @Override
    @Transactional
    public void save(User u) {
        var existing = jpa.findById(u.id().value());
        if (existing.isPresent()) {
            UserPersistenceMapper.updateEntity(existing.get(), u);
            jpa.saveAndFlush(existing.get());
        } else {
            throw new IllegalStateException(
                    "Cannot insert new User through generic repository. Use UserAccountProvisioningPort instead.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findByIds(List<UserId> ids) {
        if (ids.isEmpty()) return List.of();
        var uuids = ids.stream().map(UserId::value).toList();
        return jpa.findAllById(uuids).stream()
                .map(UserPersistenceMapper::toDomain).toList();
    }
}
