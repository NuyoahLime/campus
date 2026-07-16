package com.campusguinness.identity.application.service;

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
    public UserApplicationService(UserRepository r) { this.repo = r; }

    public UserResult create(String username) {
        if (repo.existsByUsername(username)) throw new IllegalArgumentException("Username already exists: " + username);
        var u = User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username(username));
        repo.save(u);
        return new UserResult(u.id().value(), username, u.status().name());
    }
    public UserResult activate(UUID id) { var u=find(id); u.activate(); repo.save(u); return result(u); }
    public UserResult disable(UUID id) { var u=find(id); u.disable(); repo.save(u); return result(u); }
    public UserResult reEnable(UUID id) { var u=find(id); u.reEnable(); repo.save(u); return result(u); }
    private User find(UUID id) { return repo.findById(new UserId(id)).orElseThrow(()->new IllegalArgumentException("User not found: "+id)); }
    private UserResult result(User u) { return new UserResult(u.id().value(), u.username(), u.status().name()); }
}
