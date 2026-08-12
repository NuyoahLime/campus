package com.campusguinness.school.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.school.application.port.SchoolRepository;
import com.campusguinness.school.application.result.SchoolResult;
import com.campusguinness.school.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchoolApplicationService")
class SchoolApplicationServiceTest {

    @Mock private SchoolRepository repository;
    @Mock private PlatformGovernanceAuthorization authorization;
    private SchoolApplicationService service;

    @BeforeEach void setUp() { service = new SchoolApplicationService(repository, authorization); }

    @Nested @DisplayName("Create")
    class Create {
        @Test @DisplayName("creates school in PENDING_ENABLE")
        void shouldCreate() {
            var r = service.create("test","USCC","123","INT-001","PRIMARY","Beijing","addr","name","phone","email");
            assertThat(r.status()).isEqualTo("PENDING_ENABLE");
            verify(repository).save(any(School.class));
        }
    }

    @Nested @DisplayName("Activate")
    class Activate {
        @Test @DisplayName("activates PENDING_ENABLE → NORMAL")
        void shouldActivate() {
            UUID id = UUID.randomUUID();
            when(repository.findById(any())).thenReturn(Optional.of(pendingSchool(id)));
            assertThat(service.activate(id).status()).isEqualTo("NORMAL");
            verify(repository).save(any(School.class));
        }
    }

    @Nested @DisplayName("Disable")
    class Disable {
        @Test @DisplayName("disables NORMAL → DISABLED")
        void shouldDisable() {
            UUID id = UUID.randomUUID();
            var s = pendingSchool(id); s.activate();
            when(repository.findById(any())).thenReturn(Optional.of(s));
            assertThat(service.disable(id, "reason").status()).isEqualTo("DISABLED");
            verify(repository).save(any(School.class));
        }
    }

    @Nested @DisplayName("Error cases")
    class Errors {
        @Test @DisplayName("throws when not found")
        void shouldThrowWhenNotFound() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.activate(UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repository, never()).save(any());
        }
    }

    @Test
    void rejectsDirectInvocationWithoutPlatformGovernanceAuthority() {
        when(authorization.requireSuperAdmin()).thenThrow(
                new IdentityApplicationException("PLATFORM_GOVERNANCE_DENIED", "denied"));

        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(IdentityApplicationException.class);

        verifyNoInteractions(repository);
    }

    private School pendingSchool(UUID id) {
        return School.create(new School.Builder()
                .id(new SchoolId(id)).name("test").unifiedCodeType("USCC").unifiedCode("123")
                .internalCode("INT-001").schoolType("PRIMARY").region("Beijing").address("addr")
                .contactName("name").contactPhone("phone").contactEmail("email"));
    }
}
