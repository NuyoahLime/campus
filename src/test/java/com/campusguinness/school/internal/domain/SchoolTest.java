package com.campusguinness.school.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("School aggregate")
class SchoolTest {

    private School.Builder validBuilder() {
        return new School.Builder()
                .id(new SchoolId(UUID.randomUUID()))
                .name("测试学校")
                .unifiedCodeType("USCC")
                .unifiedCode("123456789012345678")
                .internalCode("SCH-001")
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
        @DisplayName("CG-SCHOOL-001: creates in PENDING_ENABLE status")
        void shouldCreateInPendingEnableStatus() {
            var school = School.create(validBuilder());
            assertThat(school.status()).isEqualTo(SchoolStatus.PENDING_ENABLE);
        }

        @Test
        @DisplayName("CG-SCHOOL-010: null name rejected")
        void shouldRejectNullName() {
            assertThatThrownBy(() -> School.create(validBuilder().name(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-010: name over 200 chars rejected")
        void shouldRejectTooLongName() {
            assertThatThrownBy(() -> School.create(validBuilder().name("A".repeat(201))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-011: null internalCode rejected")
        void shouldRejectNullInternalCode() {
            assertThatThrownBy(() -> School.create(validBuilder().internalCode(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {

        @Test
        @DisplayName("CG-SCHOOL-001: PENDING_ENABLE → NORMAL (activate)")
        void shouldActivateFromPendingEnable() {
            var school = School.create(validBuilder());
            school.activate();
            assertThat(school.status()).isEqualTo(SchoolStatus.NORMAL);
            assertThat(school.domainEvents()).anyMatch(e -> e instanceof SchoolActivated);
        }

        @Test
        @DisplayName("CG-SCHOOL-002: NORMAL → SUSPENDED")
        void shouldSuspendFromNormal() {
            var school = School.create(validBuilder());
            school.activate();
            school.suspend("违规处理");
            assertThat(school.status()).isEqualTo(SchoolStatus.SUSPENDED);
            assertThat(school.domainEvents()).anyMatch(e -> e instanceof SchoolSuspended);
        }

        @Test
        @DisplayName("CG-SCHOOL-004: SUSPENDED → NORMAL (restore)")
        void shouldRestoreFromSuspended() {
            var school = School.create(validBuilder());
            school.activate();
            school.suspend("违规");
            school.restore();
            assertThat(school.status()).isEqualTo(SchoolStatus.NORMAL);
            assertThat(school.domainEvents()).anyMatch(e -> e instanceof SchoolRestored);
        }

        @Test
        @DisplayName("CG-SCHOOL-003: NORMAL → DISABLED")
        void shouldDisableFromNormal() {
            var school = School.create(validBuilder());
            school.activate();
            school.disable("学校关闭");
            assertThat(school.status()).isEqualTo(SchoolStatus.DISABLED);
            assertThat(school.domainEvents()).anyMatch(e -> e instanceof SchoolDisabled);
        }

        @Test
        @DisplayName("CG-SCHOOL-005: SUSPENDED → DISABLED")
        void shouldDisableFromSuspended() {
            var school = School.create(validBuilder());
            school.activate();
            school.suspend("违规");
            school.disable("最终关闭");
            assertThat(school.status()).isEqualTo(SchoolStatus.DISABLED);
        }

        @Test
        @DisplayName("CG-SCHOOL-006: DISABLED → PENDING_ENABLE (re-enable)")
        void shouldReEnableFromDisabled() {
            var school = School.create(validBuilder());
            school.activate();
            school.disable("关闭");
            school.reEnable();
            assertThat(school.status()).isEqualTo(SchoolStatus.PENDING_ENABLE);
            assertThat(school.domainEvents()).anyMatch(e -> e instanceof SchoolReEnabled);
        }

        @Test
        @DisplayName("Full lifecycle: PENDING_ENABLE→NORMAL→SUSPENDED→NORMAL→DISABLED→PENDING_ENABLE→NORMAL")
        void shouldSupportFullLifecycle() {
            var school = School.create(validBuilder());
            school.activate();
            assertThat(school.status()).isEqualTo(SchoolStatus.NORMAL);
            school.suspend("pause");
            assertThat(school.status()).isEqualTo(SchoolStatus.SUSPENDED);
            school.restore();
            assertThat(school.status()).isEqualTo(SchoolStatus.NORMAL);
            school.disable("close");
            assertThat(school.status()).isEqualTo(SchoolStatus.DISABLED);
            school.reEnable();
            assertThat(school.status()).isEqualTo(SchoolStatus.PENDING_ENABLE);
            school.activate();
            assertThat(school.status()).isEqualTo(SchoolStatus.NORMAL);
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("CG-SCHOOL-007: NORMAL → PENDING_ENABLE rejected")
        void shouldRejectActivateOnNormal() {
            var school = School.create(validBuilder());
            school.activate();
            assertThatThrownBy(school::activate)
                    .isInstanceOf(InvalidSchoolStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-008: DISABLED → NORMAL rejected (must go via PENDING_ENABLE)")
        void shouldRejectDirectNormalFromDisabled() {
            var school = School.create(validBuilder());
            school.activate();
            school.disable("close");
            assertThatThrownBy(school::activate)
                    .isInstanceOf(InvalidSchoolStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-SCHOOL-009: SUSPENDED → PENDING_ENABLE rejected")
        void shouldRejectActivateOnSuspended() {
            var school = School.create(validBuilder());
            school.activate();
            school.suspend("pause");
            assertThatThrownBy(school::activate)
                    .isInstanceOf(InvalidSchoolStateTransitionException.class);
        }

        @Test
        @DisplayName("PENDING_ENABLE → DISABLED rejected")
        void shouldRejectDisableFromPendingEnable() {
            var school = School.create(validBuilder());
            assertThatThrownBy(() -> school.disable("x"))
                    .isInstanceOf(InvalidSchoolStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtection {
        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            var school = School.create(validBuilder());
            assertThatThrownBy(() -> school.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
