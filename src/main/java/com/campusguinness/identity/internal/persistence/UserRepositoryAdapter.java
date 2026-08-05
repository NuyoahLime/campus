package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.internal.domain.SchoolMembershipId;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class UserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository jpa;
    private final SchoolMembershipJpaRepository memberships;

    UserRepositoryAdapter(UserJpaRepository r, SchoolMembershipJpaRepository memberships) {
        this.jpa = r;
        this.memberships = memberships;
    }

    @Override
    @Transactional
    public void save(User u) {
        var existing = jpa.findById(u.id().value());
        if (existing.isPresent()) {
            UserPersistenceMapper.updateEntity(existing.get(), u);
            jpa.save(existing.get());
            saveMemberships(u);
            jpa.flush();
        } else {
            throw new IllegalStateException(
                    "Cannot insert new User through generic repository. Use UserAccountProvisioningPort instead.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(this::toDomainWithMemberships);
    }

    @Override
    @Transactional
    public Optional<User> findByIdForUpdate(UserId id) {
        return jpa.findByIdForUpdate(id.value()).map(this::toDomainWithMemberships);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    private User toDomainWithMemberships(UserEntity entity) {
        var restored = memberships.findAllByUserIdOrderByStartedAtAsc(entity.getId()).stream()
                .map(SchoolMembershipPersistenceMapper::toDomain)
                .toList();
        return UserPersistenceMapper.toDomain(entity, restored);
    }

    private void saveMemberships(User user) {
        var seenIds = new HashSet<SchoolMembershipId>();
        for (var membership : user.memberships()) {
            if (!seenIds.add(membership.id())) {
                throw new IllegalStateException("duplicate membership id: " + membership.id().value());
            }
        }

        var existing = memberships.findAllByUserIdOrderByStartedAtAsc(user.id().value()).stream()
                .collect(Collectors.toMap(SchoolMembershipEntity::getId, Function.identity()));

        for (var membership : user.memberships()) {
            UUID membershipId = membership.id().value();
            var entity = existing.get(membershipId);
            if (entity == null) {
                memberships.save(SchoolMembershipPersistenceMapper.toNewEntity(
                        user.id().value(),
                        membership,
                        Instant.now()
                ));
            } else {
                SchoolMembershipPersistenceMapper.updateEntity(entity, membership);
                memberships.save(entity);
            }
        }
        memberships.flush();
    }
}
