package com.campusguinness.result.application.format;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultFormatEditApplicationIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private ResultFormatEditApplicationService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID schoolId;
    private UUID adminId;
    private UUID activityId;
    private UUID resultId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        resultId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                adminId, "fmt-" + suffix, "{noop}password", "NORMAL");
        jdbc.update("""
                INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,
                  region,address,contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, schoolId, "FMT School", "USCC", "FMT-U-" + suffix, "FMT-I-" + suffix,
                "PRIMARY", "Beijing", "Address", "Contact", "13800000000", "fmt@example.com", "NORMAL");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), adminId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by) VALUES (?,?,?,?,?,?)",
                activityId, schoolId, "FMT Activity", "PUBLISHED", "PUBLIC", adminId);
        jdbc.update("INSERT INTO activity_results(id,school_id,activity_id,result_internal_status,result_public_status) VALUES (?,?,?,?,?)",
                resultId, schoolId, activityId, "INTERNAL_PUBLISHED", "NOT_SUBMITTED");
        jdbc.update("""
                INSERT INTO result_versions(id,result_id,version_number,title,summary_text,score_highlights,
                  media_refs,is_core_content_modified,format_change_log,published_internally_at,created_at)
                VALUES (?,?,?,?,?,?::jsonb,?::jsonb,false,?,now(),now())
                """, versionId, resultId, 1, "Title", "A😊B", "[]", "[]", "unchanged");
        jdbc.update("UPDATE activity_results SET current_candidate_version_id=?,current_internal_version_id=? WHERE id=?",
                versionId, versionId, resultId);
        authenticate(adminId, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM result_format_heads WHERE result_id=?", resultId);
        jdbc.update("DELETE FROM result_format_edit_records WHERE result_id=?", resultId);
        jdbc.update("UPDATE activity_results SET current_candidate_version_id=NULL,current_internal_version_id=NULL,current_public_version_id=NULL WHERE id=?", resultId);
        jdbc.update("DELETE FROM result_review_records WHERE result_id=?", resultId);
        jdbc.update("DELETE FROM result_versions WHERE result_id=?", resultId);
        jdbc.update("DELETE FROM activity_results WHERE id=?", resultId);
        jdbc.update("DELETE FROM activities WHERE id=?", activityId);
        jdbc.update("DELETE FROM school_memberships WHERE user_id=?", adminId);
        jdbc.update("DELETE FROM schools WHERE id=?", schoolId);
        jdbc.update("DELETE FROM users WHERE id=?", adminId);
    }

    @Test
    void appendIsImmutableAndAdvancesExplicitHead() throws Exception {
        Map<String, Object> versionBefore = jdbc.queryForMap("SELECT * FROM result_versions WHERE id=?", versionId);
        Map<String, Object> resultBefore = jdbc.queryForMap("SELECT * FROM activity_results WHERE id=?", resultId);

        ResultFormatEditResult first = service.append(resultId, versionId, " first ", presentation(0, 3, 1, 2));
        ResultFormatEditResult second = service.append(resultId, versionId, "second", presentation(0, 3, 0, 1));

        assertThat(first.revision()).isOne();
        assertThat(first.reason()).isEqualTo("first");
        assertThat(second.revision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM result_format_edit_records WHERE result_version_id=?", Integer.class, versionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT current_format_edit_record_id FROM result_format_heads WHERE result_version_id=?", UUID.class, versionId)).isEqualTo(second.formatEditId());
        assertThat(jdbc.queryForMap("SELECT * FROM result_versions WHERE id=?", versionId)).isEqualTo(versionBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM activity_results WHERE id=?", resultId)).isEqualTo(resultBefore);
        assertThat(service.history(resultId, versionId)).extracting(ResultFormatHistoryEntry::revision).containsExactly(1, 2);
    }

    @Test
    void unicodeOffsetsUseCodePoints() throws Exception {
        ResultFormatEditResult result = service.append(resultId, versionId, "unicode", presentation(0, 3, 1, 2));
        assertThat(result.presentation().paragraphs().getFirst().end()).isEqualTo(3);
        assertThat(result.presentation().emphasisRanges().getFirst().start()).isOne();
    }

    @Test
    void canonicalizesEmphasisOrder() throws Exception {
        JsonNode input = mapper.readTree("""
                {"paragraphs":[{"start":0,"end":3,"style":"NORMAL"}],
                 "emphasisRanges":[{"start":2,"end":3,"style":"ITALIC"},{"start":0,"end":1,"style":"BOLD"}]}
                """);
        var result = service.append(resultId, versionId, "canonical", input);
        assertThat(result.presentation().emphasisRanges()).extracting(ResultFormatPresentation.EmphasisRange::start)
                .containsExactly(0, 2);
    }

    @Test
    void firstEditRaceHasOneWinnerAndNoOrphanAcrossTwentyRuns() throws Exception {
        for (int run = 0; run < 20; run++) {
            race(false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM result_format_heads WHERE result_version_id=?", Integer.class, versionId)).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM result_format_edit_records WHERE result_version_id=?", Integer.class, versionId)).isOne();
            jdbc.update("DELETE FROM result_format_heads WHERE result_version_id=?", versionId);
            jdbc.update("DELETE FROM result_format_edit_records WHERE result_version_id=?", versionId);
        }
    }

    @Test
    void existingHeadRaceHasOneWinnerAndNoOrphanAcrossTwentyRuns() throws Exception {
        for (int run = 0; run < 20; run++) {
            service.append(resultId, versionId, "initial", presentation(0, 3, 0, 1));
            race(true);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM result_format_heads WHERE result_version_id=?", Integer.class, versionId)).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM result_format_edit_records WHERE result_version_id=?", Integer.class, versionId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT version FROM result_format_heads WHERE result_version_id=?", Integer.class, versionId)).isEqualTo(2);
            jdbc.update("DELETE FROM result_format_heads WHERE result_version_id=?", versionId);
            jdbc.update("DELETE FROM result_format_edit_records WHERE result_version_id=?", versionId);
        }
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void rejectsInvalidPayloads(String json) throws Exception {
        assertThatThrownBy(() -> service.append(resultId, versionId, "reason", mapper.readTree(json)))
                .isInstanceOf(ResultFormatEditException.class)
                .extracting(ex -> ((ResultFormatEditException) ex).code())
                .isEqualTo("ACTIVITY_RESULT_FORMAT_INVALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM result_format_edit_records WHERE result_id=?", Integer.class, resultId)).isZero();
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                "null", "[]", "{}", "{\"unknown\":[]}",
                "{\"paragraphs\":{},\"emphasisRanges\":[]}",
                "{\"paragraphs\":[],\"emphasisRanges\":{}}",
                "{\"paragraphs\":[{\"start\":0,\"end\":3,\"style\":\"BULLET\"}],\"emphasisRanges\":[]}",
                "{\"paragraphs\":[{\"start\":0,\"end\":2,\"style\":\"NORMAL\"}],\"emphasisRanges\":[]}",
                "{\"paragraphs\":[{\"start\":1,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[]}",
                "{\"paragraphs\":[{\"start\":0,\"end\":2,\"style\":\"NORMAL\"},{\"start\":1,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[]}",
                "{\"paragraphs\":[{\"start\":0,\"end\":3,\"style\":\"NORMAL\",\"x\":1}],\"emphasisRanges\":[]}",
                "{\"paragraphs\":[{\"start\":0.5,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[]}",
                "{\"paragraphs\":[{\"start\":0,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[{\"start\":0,\"end\":4,\"style\":\"BOLD\"}]}",
                "{\"paragraphs\":[{\"start\":0,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[{\"start\":0,\"end\":1,\"style\":\"UNDERLINE\"}]}",
                "{\"paragraphs\":[{\"start\":0,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[{\"start\":0,\"end\":2,\"style\":\"BOLD\"},{\"start\":1,\"end\":3,\"style\":\"BOLD\"}]}",
                "{\"paragraphs\":[{\"start\":0,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[{\"start\":0,\"end\":1,\"style\":\"BOLD\"},{\"start\":0,\"end\":1,\"style\":\"ITALIC\"}]}",
                "{\"paragraphs\":[{\"start\":0,\"end\":3,\"style\":\"NORMAL\"}],\"emphasisRanges\":[{\"start\":0,\"end\":1,\"style\":\"BOLD\",\"x\":1}]}"
        ).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    void rejectsInvalidReasons(String reason) throws Exception {
        assertThatThrownBy(() -> service.append(resultId, versionId, reason, presentation(0, 3, 0, 1)))
                .isInstanceOf(ResultFormatEditException.class);
    }

    static Stream<String> invalidReasons() {
        return Stream.of(null, "", "   ", "x".repeat(2001));
    }

    @ParameterizedTest
    @MethodSource("deniedStates")
    void deniesIneligibleLifecycleStates(String column, String state) throws Exception {
        jdbc.update("UPDATE " + (column.equals("execution_status") ? "activities" : "activity_results")
                + " SET " + column + "=? WHERE id=?", state, column.equals("execution_status") ? activityId : resultId);
        assertThatThrownBy(() -> service.append(resultId, versionId, "reason", presentation(0, 3, 0, 1)))
                .isInstanceOf(ResultFormatEditException.class)
                .extracting(ex -> ((ResultFormatEditException) ex).code())
                .isEqualTo("ACTIVITY_RESULT_FORMAT_TARGET_CONFLICT");
    }

    static Stream<Arguments> deniedStates() {
        return Stream.of(
                Arguments.of("execution_status", "DRAFT"),
                Arguments.of("execution_status", "CANCELLED"),
                Arguments.of("result_public_status", "ANOMALY_PENDING"),
                Arguments.of("result_public_status", "PLATFORM_TAKEDOWN"));
    }

    @ParameterizedTest(name = "matrix-{0}")
    @MethodSource("exactVersionMatrix")
    void exactVersionEligibilityMatrix(String name, String candidate, String internal, String publicPointer,
            String publicStatus, boolean blocked, String internalStatus, String target, boolean allowed) throws Exception {
        UUID v2 = UUID.randomUUID();
        jdbc.update("INSERT INTO result_versions(id,result_id,version_number,title,summary_text,score_highlights,media_refs,is_core_content_modified,published_internally_at) VALUES (?,?,?,?,?,?::jsonb,?::jsonb,false,now())",
                v2,resultId,2,"V2","A😊B","[]","[]");
        jdbc.update("UPDATE activity_results SET current_candidate_version_id=?,current_internal_version_id=?,current_public_version_id=?,result_public_status=?,public_visibility_blocked=?,result_internal_status=? WHERE id=?",
                pointer(candidate,v2),pointer(internal,v2),pointer(publicPointer,v2),publicStatus,blocked,internalStatus,resultId);
        UUID targetId = "V2".equals(target) ? v2 : versionId;
        if (allowed) {
            assertThat(service.append(resultId,targetId,"matrix",presentation(0,3,0,1)).resultVersionId()).isEqualTo(targetId);
        } else {
            assertThatThrownBy(() -> service.append(resultId,targetId,"matrix",presentation(0,3,0,1)))
                    .isInstanceOf(ResultFormatEditException.class);
        }
        jdbc.update("DELETE FROM result_format_heads WHERE result_id=?",resultId);
        jdbc.update("DELETE FROM result_format_edit_records WHERE result_id=?",resultId);
        jdbc.update("UPDATE activity_results SET current_candidate_version_id=?,current_internal_version_id=?,current_public_version_id=NULL WHERE id=?",versionId,versionId,resultId);
        jdbc.update("DELETE FROM result_versions WHERE id=?",v2);
    }

    static Stream<Arguments> exactVersionMatrix() {
        return Stream.of(
                Arguments.of("first-not-submitted","V1","V1",null,"NOT_SUBMITTED",false,"INTERNAL_PUBLISHED","V1",true),
                Arguments.of("first-pending","V1","V1",null,"PENDING_PUBLIC_REVIEW",false,"INTERNAL_PUBLISHED","V1",false),
                Arguments.of("first-approved","V1","V1",null,"PLATFORM_APPROVED",false,"INTERNAL_PUBLISHED","V1",false),
                Arguments.of("public","NONE","V1","V1","PUBLIC",false,"INTERNAL_PUBLISHED","V1",true),
                Arguments.of("replacement-draft-candidate","V2","V1","V1","NOT_SUBMITTED",false,"DRAFT","V2",false),
                Arguments.of("replacement-draft-old-public","V2","V1","V1","NOT_SUBMITTED",false,"DRAFT","V1",true),
                Arguments.of("replacement-internal","V2","V2","V1","NOT_SUBMITTED",false,"INTERNAL_PUBLISHED","V2",true),
                Arguments.of("replacement-pending-old-public","V2","V2","V1","PENDING_PUBLIC_REVIEW",false,"INTERNAL_PUBLISHED","V1",true),
                Arguments.of("replacement-pending-candidate","V2","V2","V1","PENDING_PUBLIC_REVIEW",false,"INTERNAL_PUBLISHED","V2",false),
                Arguments.of("replacement-approved-candidate","V2","V2","V1","PLATFORM_APPROVED",false,"INTERNAL_PUBLISHED","V2",false),
                Arguments.of("replacement-approved-old-public","V2","V2","V1","PLATFORM_APPROVED",false,"INTERNAL_PUBLISHED","V1",true),
                Arguments.of("replacement-rejected-candidate","V2","V2","V1","PLATFORM_REJECTED",false,"INTERNAL_PUBLISHED","V2",false),
                Arguments.of("replacement-rejected-old-public","V2","V2","V1","PLATFORM_REJECTED",false,"INTERNAL_PUBLISHED","V1",true),
                Arguments.of("reset-blocked-public","NONE","V2","V1","NOT_SUBMITTED",true,"INTERNAL_PUBLISHED","V1",false),
                Arguments.of("post-reset-exact-internal","V2","V2","V1","NOT_SUBMITTED",true,"INTERNAL_PUBLISHED","V2",true),
                Arguments.of("historical-non-current","V2","V2",null,"NOT_SUBMITTED",false,"INTERNAL_PUBLISHED","V1",false),
                Arguments.of("withdrawn-internal","NONE","V1",null,"NOT_SUBMITTED",false,"INTERNAL_WITHDRAWN","V1",false),
                Arguments.of("withdrawn-active-public","NONE","V2","V1","NOT_SUBMITTED",false,"INTERNAL_WITHDRAWN","V1",true));
    }

    private UUID pointer(String value, UUID v2) {
        if (value == null || "NONE".equals(value)) return null;
        return "V2".equals(value) ? v2 : versionId;
    }

    private JsonNode presentation(int paragraphStart, int paragraphEnd, int emphasisStart, int emphasisEnd) throws Exception {
        return mapper.readTree("""
                {"paragraphs":[{"start":%d,"end":%d,"style":"NORMAL"}],
                 "emphasisRanges":[{"start":%d,"end":%d,"style":"BOLD"}]}
                """.formatted(paragraphStart, paragraphEnd, emphasisStart, emphasisEnd));
    }

    private void race(boolean existingHead) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = executor.submit(() -> raceCall(barrier, "a"));
            Future<Boolean> b = executor.submit(() -> raceCall(barrier, "b"));
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }
    }

    private boolean raceCall(CyclicBarrier barrier, String reason) {
        try {
            authenticate(adminId, "SCHOOL_ADMIN", schoolId, "SCHOOL_ADMIN");
            barrier.await();
            service.append(resultId, versionId, reason, presentation(0, 3, 0, 1));
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(UUID userId, String role, UUID actorSchoolId, String membershipRole) {
        var details = new CampusGuinnessUserDetails(userId, "fmt-principal", "{noop}password", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)),
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), actorSchoolId, membershipRole)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }
}
