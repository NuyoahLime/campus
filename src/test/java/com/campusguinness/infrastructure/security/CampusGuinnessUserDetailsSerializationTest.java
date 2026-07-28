package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;
import com.campusguinness.infrastructure.security.PrimaryIdentityResolver.ResolvedIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CampusGuinnessUserDetailsSerializationTest {

    @Test
    void principalObjectGraphCanRoundTripThroughJavaSerialization() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();

        var identity = new ResolvedIdentity(userId, "SCHOOL_ADMIN", schoolId, "NORMAL");
        var original = new CampusGuinnessUserDetails(userId, "school-admin", "hash", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")),
                List.of(new SchoolMembershipRecord(schoolId, "SCHOOL_ADMIN")), identity);

        byte[] serialized;
        try (var baos = new ByteArrayOutputStream(); var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
            oos.flush();
            serialized = baos.toByteArray();
        }

        CampusGuinnessUserDetails restored;
        try (var bais = new ByteArrayInputStream(serialized); var ois = new ObjectInputStream(bais)) {
            restored = (CampusGuinnessUserDetails) ois.readObject();
        }

        assertThat(restored.getUserId()).isEqualTo(userId);
        assertThat(restored.getUsername()).isEqualTo("school-admin");
        assertThat(restored.getResolvedIdentity().primarySchoolId()).isEqualTo(schoolId);
        assertThat(restored.getSchoolMemberships()).containsExactly(new SchoolMembershipRecord(schoolId, "SCHOOL_ADMIN"));
        assertThat(restored.getAuthorities()).extracting("authority").containsExactly("ROLE_SCHOOL_ADMIN");
    }
}
