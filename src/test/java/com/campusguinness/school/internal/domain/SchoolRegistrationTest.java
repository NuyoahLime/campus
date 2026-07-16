package com.campusguinness.school.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SchoolRegistration aggregate")
class SchoolRegistrationTest {

    private SchoolRegistration.Builder validBuilder() {
        return new SchoolRegistration.Builder()
                .id(new SchoolRegistrationId(UUID.randomUUID()))
                .schoolName("测试学校")
                .unifiedCodeType("USCC")
                .unifiedCode("123456789012345678")
                .schoolType("PRIMARY")
                .region("Beijing")
                .address("测试地址")
                .contactName("张三")
                .contactPhone("13800000000")
                .contactEmail("test@school.com");
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("CG-SCHOOL-REG-001: creates in DRAFT status")
        void shouldCreateInDraftStatus() {
            var reg = SchoolRegistration.create(validBuilder());
            assertThat(reg.status()).isEqualTo(RegistrationStatus.DRAFT);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-012: null schoolName rejected")
        void shouldRejectNullSchoolName() {
            assertThatThrownBy(() -> SchoolRegistration.create(validBuilder().schoolName(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-012: blank schoolName rejected")
        void shouldRejectBlankSchoolName() {
            assertThatThrownBy(() -> SchoolRegistration.create(validBuilder().schoolName("  ")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-012: schoolName over 200 chars rejected")
        void shouldRejectTooLongSchoolName() {
            assertThatThrownBy(() -> SchoolRegistration.create(validBuilder().schoolName("A".repeat(201))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-013: null unifiedCodeType rejected")
        void shouldRejectNullUnifiedCodeType() {
            assertThatThrownBy(() -> SchoolRegistration.create(validBuilder().unifiedCodeType(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-014: null schoolType rejected")
        void shouldRejectNullSchoolType() {
            assertThatThrownBy(() -> SchoolRegistration.create(validBuilder().schoolType(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {

        @Test
        @DisplayName("CG-SCHOOL-REG-001: DRAFT → SUBMITTED")
        void shouldSubmitFromDraft() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            assertThat(reg.status()).isEqualTo(RegistrationStatus.SUBMITTED);
            assertThat(reg.domainEvents()).anyMatch(e -> e instanceof SchoolRegistrationSubmitted);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-003: SUBMITTED → APPROVED (terminal)")
        void shouldApproveFromSubmitted() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            UUID schoolId = UUID.randomUUID();
            reg.approve(UUID.randomUUID(), "ok", schoolId);
            assertThat(reg.status()).isEqualTo(RegistrationStatus.APPROVED);
            assertThat(reg.createdSchoolId()).isEqualTo(schoolId);
            assertThat(reg.domainEvents()).anyMatch(e -> e instanceof SchoolRegistrationApproved);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-004: SUBMITTED → REJECTED (terminal)")
        void shouldRejectFromSubmitted() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            reg.reject(UUID.randomUUID(), "不符合条件");
            assertThat(reg.status()).isEqualTo(RegistrationStatus.REJECTED);
            assertThat(reg.rejectReason()).isEqualTo("不符合条件");
            assertThat(reg.domainEvents()).anyMatch(e -> e instanceof SchoolRegistrationRejected);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-005: SUBMITTED → WITHDRAWN (terminal)")
        void shouldWithdrawFromSubmitted() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            reg.withdraw();
            assertThat(reg.status()).isEqualTo(RegistrationStatus.WITHDRAWN);
            assertThat(reg.domainEvents()).anyMatch(e -> e instanceof SchoolRegistrationWithdrawn);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-002: SUBMITTED → NEED_SUPPLEMENT → RESUBMIT")
        void shouldSupplementAndResubmit() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            reg.requestSupplement(UUID.randomUUID(), "请补充材料");
            assertThat(reg.status()).isEqualTo(RegistrationStatus.NEED_SUPPLEMENT);

            reg.resubmit();
            assertThat(reg.status()).isEqualTo(RegistrationStatus.SUBMITTED);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-007: NEED_SUPPLEMENT → WITHDRAWN")
        void shouldWithdrawFromNeedSupplement() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            reg.requestSupplement(UUID.randomUUID(), "补充");
            reg.withdraw();
            assertThat(reg.status()).isEqualTo(RegistrationStatus.WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("CG-SCHOOL-REG-008: DRAFT → APPROVED rejected")
        void shouldRejectDirectApproveFromDraft() {
            var reg = SchoolRegistration.create(validBuilder());
            assertThatThrownBy(() -> reg.approve(UUID.randomUUID(), "x", UUID.randomUUID()))
                    .isInstanceOf(InvalidRegistrationStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-REG-010: APPROVED is terminal")
        void approvedIsTerminal() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            reg.approve(UUID.randomUUID(), "ok", UUID.randomUUID());
            assertThatThrownBy(reg::submit)
                    .isInstanceOf(InvalidRegistrationStateTransitionException.class);
            assertThatThrownBy(reg::withdraw)
                    .isInstanceOf(InvalidRegistrationStateTransitionException.class);
        }

        @Test
        @DisplayName("REJECTED is terminal")
        void rejectedIsTerminal() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            reg.reject(UUID.randomUUID(), "rejected");
            assertThatThrownBy(reg::submit)
                    .isInstanceOf(InvalidRegistrationStateTransitionException.class);
        }

        @Test
        @DisplayName("WITHDRAWN is terminal")
        void withdrawnIsTerminal() {
            var reg = SchoolRegistration.create(validBuilder());
            reg.submit();
            reg.withdraw();
            assertThatThrownBy(reg::submit)
                    .isInstanceOf(InvalidRegistrationStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtection {
        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            var reg = SchoolRegistration.create(validBuilder());
            assertThatThrownBy(() -> reg.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
