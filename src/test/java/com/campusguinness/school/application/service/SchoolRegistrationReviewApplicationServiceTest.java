package com.campusguinness.school.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.school.application.exception.SchoolRegistrationReviewException;
import com.campusguinness.school.application.port.SchoolRegistrationRepository;
import com.campusguinness.school.application.port.SchoolRepository;
import com.campusguinness.school.internal.domain.School;
import com.campusguinness.school.internal.domain.SchoolRegistration;
import com.campusguinness.school.internal.domain.SchoolRegistrationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolRegistrationReviewApplicationServiceTest {

    @Mock SchoolRegistrationRepository registrations;
    @Mock SchoolRepository schools;
    @Mock PlatformGovernanceAuthorization authorization;
    SchoolRegistrationReviewApplicationService service;
    UUID reviewerId;

    @BeforeEach
    void setUp() {
        reviewerId = UUID.randomUUID();
        lenient().when(authorization.requireSuperAdmin()).thenReturn(reviewerId);
        service = new SchoolRegistrationReviewApplicationService(registrations, schools, authorization);
    }

    @Test
    void requestsSupplementWithAuthoritativeReviewerAndTrimmedComment() {
        SchoolRegistration registration = submitted("UAT-1");
        when(registrations.findById(any())).thenReturn(Optional.of(registration));

        var result = service.requestSupplement(registration.id().value(), "  add license  ");

        assertThat(result.status()).isEqualTo("NEED_SUPPLEMENT");
        assertThat(registration.reviewedBy()).isEqualTo(reviewerId);
        assertThat(registration.reviewedAt()).isNotNull();
        assertThat(registration.reviewComment()).isEqualTo("add license");
        verify(registrations).save(registration);
    }

    @Test
    void approvesByCreatingPendingSchoolFromPersistedRegistrationData() {
        SchoolRegistration registration = submitted("UAT-2");
        when(registrations.findById(any())).thenReturn(Optional.of(registration));
        when(schools.existsByUnifiedCode("USCC", "UAT-2")).thenReturn(false);

        var result = service.approve(registration.id().value(), " verified ");

        ArgumentCaptor<School> schoolCaptor = ArgumentCaptor.forClass(School.class);
        verify(schools).save(schoolCaptor.capture());
        School school = schoolCaptor.getValue();
        assertThat(school.id().value()).isEqualTo(result.createdSchoolId());
        assertThat(school.internalCode()).hasSize(32).doesNotContain("-");
        assertThat(school.status().name()).isEqualTo("PENDING_ENABLE");
        assertThat(school.name()).isEqualTo(registration.schoolName());
        assertThat(registration.status().name()).isEqualTo("APPROVED");
        assertThat(registration.createdSchoolId()).isEqualTo(school.id().value());
        assertThat(registration.reviewedBy()).isEqualTo(reviewerId);
        assertThat(registration.reviewComment()).isEqualTo("verified");
        verify(registrations).save(registration);
    }

    @Test
    void approvesRegistrationWithoutUnifiedCode() {
        SchoolRegistration registration = submitted(null);
        when(registrations.findById(any())).thenReturn(Optional.of(registration));

        service.approve(registration.id().value(), null);

        ArgumentCaptor<School> captor = ArgumentCaptor.forClass(School.class);
        verify(schools).save(captor.capture());
        assertThat(captor.getValue().unifiedCode()).isNull();
        verify(schools, never()).existsByUnifiedCode(any(), any());
    }

    @Test
    void rejectsWithAuthoritativeReviewerAndTrimmedReason() {
        SchoolRegistration registration = submitted("UAT-3");
        when(registrations.findById(any())).thenReturn(Optional.of(registration));

        var result = service.reject(registration.id().value(), "  mismatched data  ");

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(registration.reviewedBy()).isEqualTo(reviewerId);
        assertThat(registration.reviewedAt()).isNotNull();
        assertThat(registration.rejectReason()).isEqualTo("mismatched data");
        verify(registrations).save(registration);
    }

    @Test
    void duplicateUnifiedCodeStopsApprovalBeforeSchoolCreation() {
        SchoolRegistration registration = submitted("DUPLICATE");
        when(registrations.findById(any())).thenReturn(Optional.of(registration));
        when(schools.existsByUnifiedCode("USCC", "DUPLICATE")).thenReturn(true);

        assertThatThrownBy(() -> service.approve(registration.id().value(), "ok"))
                .isInstanceOf(SchoolRegistrationReviewException.class)
                .extracting("code").isEqualTo("SCHOOL_UNIFIED_CODE_CONFLICT");

        assertThat(registration.status().name()).isEqualTo("SUBMITTED");
        verify(schools, never()).save(any());
        verify(registrations, never()).save(any());
    }

    @Test
    void authorizationFailurePreventsAllRepositoryAccess() {
        when(authorization.requireSuperAdmin()).thenThrow(
                new IdentityApplicationException("PLATFORM_GOVERNANCE_DENIED", "denied"));

        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), "ok"))
                .isInstanceOf(IdentityApplicationException.class);

        verifyNoInteractions(registrations, schools);
    }

    private SchoolRegistration submitted(String unifiedCode) {
        SchoolRegistration registration = SchoolRegistration.create(new SchoolRegistration.Builder()
                .id(new SchoolRegistrationId(UUID.randomUUID()))
                .schoolName("Campus UAT School")
                .unifiedCodeType("USCC")
                .unifiedCode(unifiedCode)
                .schoolType("UNIVERSITY")
                .region("Zhejiang")
                .address("Address")
                .contactName("Contact")
                .contactPhone("13800000000")
                .contactEmail("uat@example.com"));
        registration.submit();
        return registration;
    }
}
