package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityRequestContractTest {

    private static final List<FieldContract> REMOVED_ACTOR_FIELDS = List.of(
            new FieldContract("com.campusguinness.interfaces.web.activity.CreateActivityRequest", "createdBy"),
            new FieldContract("com.campusguinness.interfaces.web.rankingdefinition.CreateRankingDefinitionRequest", "createdBy"),
            new FieldContract("com.campusguinness.interfaces.web.activityapplication.ApproveActivityApplicationRequest", "reviewerId"),
            new FieldContract("com.campusguinness.interfaces.web.activityapplication.RejectActivityApplicationRequest", "reviewerId"),
            new FieldContract("com.campusguinness.interfaces.web.schoolregistration.ApproveSchoolRegistrationRequest", "reviewerId"),
            new FieldContract("com.campusguinness.interfaces.web.schoolregistration.RejectSchoolRegistrationRequest", "reviewerId"),
            new FieldContract("com.campusguinness.interfaces.web.l3authorization.ApproveL3AuthorizationRequest", "reviewerId"),
            new FieldContract("com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest", "studentId"),
            new FieldContract("com.campusguinness.interfaces.web.feedback.SubmitFeedbackRequest", "submitterId"),
            new FieldContract("com.campusguinness.interfaces.web.activityapplication.SubmitActivityApplicationRequest", "applicantId"),
            new FieldContract("com.campusguinness.interfaces.web.media.RegisterMediaRequest", "uploaderId")
    );

    @Test
    void phase8MustRemoveFieldsAreAbsentFromRequestDtos() throws Exception {
        for (FieldContract field : REMOVED_ACTOR_FIELDS) {
            assertThat(recordComponentNames(field.className()))
                    .as("%s must not expose request-controlled %s", field.className(), field.fieldName())
                    .doesNotContain(field.fieldName());
        }
    }

    @Test
    void legitimateTargetStudentIdRemainsOnScoreSubmitRequest() throws Exception {
        assertThat(recordComponentNames("com.campusguinness.interfaces.web.scoreattempt.SubmitScoreRequest"))
                .contains("studentId");
    }

    @Test
    void unusedActivateSchoolRequestIsDeleted() {
        assertThatThrownBy(() -> Class.forName("com.campusguinness.interfaces.web.school.ActivateSchoolRequest"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void applicationLayerDoesNotReadSpringSecurityContextDirectly() throws IOException {
        try (Stream<java.nio.file.Path> files = Files.walk(Paths.get("src/main/java/com/campusguinness"))) {
            List<String> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/application/"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> line.contains("SecurityContextHolder"))
                                    .map(line -> path + ": " + line.trim());
                        } catch (IOException e) {
                            return Stream.of(path + ": ERROR reading file");
                        }
                    })
                    .toList();

            assertThat(violations)
                    .as("Application layer must use CurrentActor instead of SecurityContextHolder")
                    .isEmpty();
        }
    }

    private static Set<String> recordComponentNames(String className) throws ClassNotFoundException {
        Class<?> type = Class.forName(className);
        return Stream.of(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private record FieldContract(String className, String fieldName) {}
}
