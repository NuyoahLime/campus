package com.campusguinness.ranking.application.port;
import com.campusguinness.ranking.internal.domain.L3Authorization;
import com.campusguinness.ranking.internal.domain.L3AuthorizationId;
import java.util.Optional;
public interface L3AuthorizationRepository { void save(L3Authorization a); Optional<L3Authorization> findById(L3AuthorizationId id); }
