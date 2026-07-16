package com.campusguinness.identity.application.port;
import com.campusguinness.identity.internal.domain.User;
import com.campusguinness.identity.internal.domain.UserId;
import java.util.Optional;
public interface UserRepository { void save(User u); Optional<User> findById(UserId id); boolean existsByUsername(String username); }
