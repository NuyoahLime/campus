package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("L3AuthorizationPersistenceMapper")
class L3AuthorizationPersistenceMapperTest {
    @Nested class ToDomain {
        @Test void restoresApproved() {
            var e = entity("APPROVED"); e.setReviewedBy(UUID.randomUUID()); e.setReviewComment("ok");
            var a = L3AuthorizationPersistenceMapper.toDomain(e);
            assertThat(a.status()).isEqualTo(AuthorizationStatus.APPROVED);
            assertThat(a.reviewComment()).isEqualTo("ok");
            assertThat(a.domainEvents()).isEmpty();
        }
        @Test void restoresWithdrawn() {
            var e = entity("WITHDRAWN"); e.setWithdrawReason("school disabled");
            var a = L3AuthorizationPersistenceMapper.toDomain(e);
            assertThat(a.status()).isEqualTo(AuthorizationStatus.WITHDRAWN);
            assertThat(a.domainEvents()).isEmpty();
        }
    }
    @Nested class ToEntity {
        @Test void mapsToEntity() {
            var a = L3Authorization.create(new L3Authorization.Builder()
                    .id(new L3AuthorizationId(UUID.randomUUID())).schoolId(UUID.randomUUID())
                    .projectId(UUID.randomUUID()).ruleVersionId(UUID.randomUUID()));
            var e = L3AuthorizationPersistenceMapper.toEntity(a);
            assertThat(e.getAuthorizationStatus()).isEqualTo("DRAFT");
        }
    }
    private L3AuthorizationEntity entity(String s) {
        var e = new L3AuthorizationEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID());
        e.setProjectId(UUID.randomUUID()); e.setRuleVersionId(UUID.randomUUID());
        e.setAuthorizationStatus(s);
        return e;
    }
}
