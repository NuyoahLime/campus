package com.campusguinness.infrastructure.security.session;

import org.junit.jupiter.api.*;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpringSessionUserSessionRevokerTest {

    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<Session> repo = mock(FindByIndexNameSessionRepository.class);

    private final SpringSessionUserSessionRevoker revoker = new SpringSessionUserSessionRevoker(repo);

    private static final String USERNAME = "testuser";

    @Test void noSessionsDoesNothing() {
        when(repo.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, USERNAME))
                .thenReturn(Map.of());

        revoker.revokeAllSessions(USERNAME);

        verify(repo, never()).deleteById(any());
    }

    @Test void allSessionsAreDeleted() {
        Session s1 = new MapSession("id-1");
        Session s2 = new MapSession("id-2");
        when(repo.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, USERNAME))
                .thenReturn(Map.of("id-1", s1, "id-2", s2));

        revoker.revokeAllSessions(USERNAME);

        verify(repo).deleteById("id-1");
        verify(repo).deleteById("id-2");
    }

    @Test void deleteFailureIsPropagated() {
        Session s1 = new MapSession("id-1");
        Session s2 = new MapSession("id-2");
        when(repo.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, USERNAME))
                .thenReturn(Map.of("id-1", s1, "id-2", s2));
        doThrow(new RuntimeException("DB error"))
                .when(repo).deleteById("id-1");

        assertThatThrownBy(() -> revoker.revokeAllSessions(USERNAME))
                .isInstanceOf(SessionRevocationException.class)
                .matches(e -> ((SessionRevocationException) e).getFoundCount() == 2)
                .matches(e -> ((SessionRevocationException) e).getRevokedCount() == 1)
                .matches(e -> ((SessionRevocationException) e).getFailedSessionIds().contains("id-1"));

        // id-2 must still be attempted
        verify(repo).deleteById("id-2");
    }

    @Test void deleteFailureDoesNotStopRemainingDeletionAttempts() {
        Session s1 = new MapSession("id-1");
        Session s2 = new MapSession("id-2");
        Session s3 = new MapSession("id-3");
        when(repo.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, USERNAME))
                .thenReturn(Map.of("id-1", s1, "id-2", s2, "id-3", s3));
        doThrow(new RuntimeException("fail")).when(repo).deleteById("id-2");

        assertThatThrownBy(() -> revoker.revokeAllSessions(USERNAME))
                .isInstanceOf(SessionRevocationException.class)
                .matches(e -> ((SessionRevocationException) e).getRevokedCount() == 2);

        verify(repo).deleteById("id-1");
        verify(repo).deleteById("id-2"); // failed but attempted
        verify(repo).deleteById("id-3"); // attempted after failure
    }

    @Test void findFailureIsPropagated() {
        when(repo.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, USERNAME))
                .thenThrow(new RuntimeException("DB lookup error"));

        assertThatThrownBy(() -> revoker.revokeAllSessions(USERNAME))
                .isInstanceOf(RuntimeException.class);

        verify(repo, never()).deleteById(any());
    }
}
