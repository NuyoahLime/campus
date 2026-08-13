package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.exception.UsernameAlreadyExistsException;
import com.campusguinness.identity.application.port.PasswordHasher;
import com.campusguinness.identity.application.port.PasswordPolicy;
import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.result.UserResult;
import com.campusguinness.identity.internal.domain.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UserApplicationService {
    private final UserRepository repo;
    private final UserAccountProvisioningPort provisioning;
    private final PasswordHasher hasher;
    private final PlatformGovernanceAuthorization authorization;

    public UserApplicationService(
            UserRepository repo,
            UserAccountProvisioningPort provisioning,
            PasswordHasher hasher,
            PlatformGovernanceAuthorization authorization
    ) {
        this.repo = repo;
        this.provisioning = provisioning;
        this.hasher = hasher;
        this.authorization = authorization;
    }

    /**
     * Create a new ordinary user with an initial password.
     * The user is created in PENDING_ACTIVATION state with platformRole=null.
     */
    public UserResult create(String username, String rawPassword) {
        authorization.requireSuperAdmin();
        String normalized = username != null ? username.trim() : "";
        if (normalized.isEmpty()) throw new IllegalArgumentException("username must not be blank");
        PasswordPolicy.validate(rawPassword);
        if (repo.existsByUsername(normalized)) throw new UsernameAlreadyExistsException(normalized);

        String passwordHash = hasher.hash(rawPassword);
        var user = User.create(new User.Builder()
                .id(new UserId(UUID.randomUUID()))
                .username(normalized));
        // platformRole deliberately null for ordinary users

        var saved = provisioning.create(user, passwordHash);
        return result(saved);
    }

    public UserResult activate(UUID id) {
        authorization.requireSuperAdmin();
        var user = find(id);
        user.activate();
        repo.save(user);
        return result(user);
    }

    public UserResult disable(UUID id) {
        authorization.requireSuperAdmin();
        var user = find(id);
        user.disable();
        repo.save(user);
        return result(user);
    }

    public UserResult reEnable(UUID id) {
        authorization.requireSuperAdmin();
        var user = find(id);
        user.reEnable();
        repo.save(user);
        return result(user);
    }

    private User find(UUID id) { return repo.findById(new UserId(id)).orElseThrow(()->new IllegalArgumentException("User not found: "+id)); }
    private UserResult result(User u) { return new UserResult(u.id().value(), u.username(), u.status().name()); }
}
