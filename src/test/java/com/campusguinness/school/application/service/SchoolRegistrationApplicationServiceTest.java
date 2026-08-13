package com.campusguinness.school.application.service;

import com.campusguinness.school.application.command.SubmitSchoolRegistrationCommand;
import com.campusguinness.school.application.port.SchoolRegistrationRepository;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
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
@DisplayName("SchoolRegistrationApplicationService")
class SchoolRegistrationApplicationServiceTest {

    @Mock private SchoolRegistrationRepository repository;
    private SchoolRegistrationApplicationService service;

    @BeforeEach void setUp() {
        service = new SchoolRegistrationApplicationService(repository);
    }

    private SubmitSchoolRegistrationCommand validCmd() {
        return new SubmitSchoolRegistrationCommand("测试学校","USCC","1234567890","PRIMARY",
                "Beijing","address","张三","13800000000","test@test.com","desc","file-key");
    }

    @Nested @DisplayName("Submit")
    class Submit {
        @Test @DisplayName("submits in SUBMITTED status")
        void shouldSubmit() {
            var r = service.submit(validCmd());
            assertThat(r.status()).isEqualTo("SUBMITTED");
            verify(repository).save(any(SchoolRegistration.class));
        }
    }


    @Nested @DisplayName("Withdraw")
    class Withdraw {
        @Test @DisplayName("withdraws from SUBMITTED")
        void shouldWithdraw() {
            UUID id = UUID.randomUUID();
            when(repository.findById(any())).thenReturn(Optional.of(submittedReg(id)));
            var r = service.withdraw(id);
            assertThat(r.status()).isEqualTo("WITHDRAWN");
            verify(repository).save(any(SchoolRegistration.class));
        }
    }

    @Nested @DisplayName("Error cases")
    class Errors {
        @Test @DisplayName("throws when not found")
        void shouldThrowWhenNotFound() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.withdraw(UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repository, never()).save(any());
        }
    }

    private SchoolRegistration submittedReg(UUID id) {
        var reg = SchoolRegistration.create(new SchoolRegistration.Builder()
                .id(new SchoolRegistrationId(id)).schoolName("test").unifiedCodeType("USCC")
                .schoolType("PRIMARY").region("Beijing").address("addr")
                .contactName("name").contactPhone("phone").contactEmail("email"));
        reg.submit();
        return reg;
    }
}
