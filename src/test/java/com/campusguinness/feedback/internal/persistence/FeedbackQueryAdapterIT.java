package com.campusguinness.feedback.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class FeedbackQueryAdapterIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private FeedbackQueryAdapter adapter;
    @Autowired private JdbcTemplate jdbc;

    private UUID schoolA;
    private UUID schoolB;
    private UUID studentA;
    private UUID studentB;
    private UUID feedbackA;
    private UUID feedbackB;

    @BeforeEach
    void setUp() {
        schoolA = insertSchool("A");
        schoolB = insertSchool("B");
        studentA = insertUser("student-a");
        studentB = insertUser("student-b");
        feedbackA = insertFeedback(schoolA, studentA, "SUBMITTED");
        feedbackB = insertFeedback(schoolB, studentB, "PROCESSING");
    }

    @Test
    void studentScopeListsAndReadsOnlyOwnFeedback() {
        var page = adapter.findByStudent(studentA, schoolA, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).extracting("feedbackId").containsExactly(feedbackA);
        assertThat(adapter.findByIdAndStudent(feedbackA, studentA, schoolA)).isPresent();
        assertThat(adapter.findByIdAndStudent(feedbackB, studentA, schoolA)).isEmpty();
    }

    @Test
    void schoolScopeListsAndReadsOnlySameSchoolFeedback() {
        var page = adapter.findBySchool(schoolA, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).extracting("feedbackId").containsExactly(feedbackA);
        assertThat(adapter.findByIdAndSchool(feedbackA, schoolA)).isPresent();
        assertThat(adapter.findByIdAndSchool(feedbackB, schoolA)).isEmpty();
    }

    private UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, "Stage21 Feedback School " + label, "USCC", "ST21-F-" + label + "-" + id.toString().substring(0, 8),
                "ST21-FI-" + label + "-" + id.toString().substring(0, 8), "UNIVERSITY", "Region", "Address",
                "Contact", "13800000000", "stage21-feedback-" + label + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                id, "stage21-feedback-" + label + "-" + id.toString().substring(0, 8), "{noop}password", "NORMAL");
        return id;
    }

    private UUID insertFeedback(UUID schoolId, UUID studentId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO feedbacks(id,school_id,submitter_id,feedback_type,content,feedback_status) VALUES (?,?,?,?,?,?)",
                id, schoolId, studentId, "GENERAL", "Stage21 feedback " + id, status);
        return id;
    }
}
