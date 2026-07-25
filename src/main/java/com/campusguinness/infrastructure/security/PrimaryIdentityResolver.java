package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.query.AuthenticationAccount;
import com.campusguinness.identity.application.query.AuthenticationAccount.SchoolMembershipRecord;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PrimaryIdentityResolver {

    public ResolvedIdentity resolve(AuthenticationAccount account) {
        boolean isSuperAdmin = "SUPER_ADMIN".equals(account.platformRole());
        List<SchoolMembershipRecord> memberships = account.memberships();

        if (isSuperAdmin && memberships.isEmpty()) {
            return new ResolvedIdentity(account.userId(), "SUPER_ADMIN", null, account.accountStatus());
        }

        if (isSuperAdmin && !memberships.isEmpty()) {
            return ResolvedIdentity.ambiguous(account.userId());
        }

        // No platform role — must have exactly one ACTIVE school membership
        if (account.platformRole() == null) {
            if (memberships.size() == 1) {
                var m = memberships.getFirst();
                return new ResolvedIdentity(account.userId(), m.roleInSchool(), m.schoolId(), account.accountStatus());
            }
            if (memberships.isEmpty()) return ResolvedIdentity.notAssigned(account.userId());
            return ResolvedIdentity.ambiguous(account.userId());
        }

        // Unknown platform_role
        return ResolvedIdentity.invalid(account.userId());
    }

    public record ResolvedIdentity(UUID userId, String primaryRole, UUID primarySchoolId,
            String accountStatus, String errorCode) {

        public ResolvedIdentity(UUID userId, String primaryRole, UUID primarySchoolId, String accountStatus) {
            this(userId, primaryRole, primarySchoolId, accountStatus, null);
        }

        public static ResolvedIdentity notAssigned(UUID userId) {
            return new ResolvedIdentity(userId, null, null, "NORMAL", "IDENTITY_NOT_ASSIGNED");
        }

        public static ResolvedIdentity ambiguous(UUID userId) {
            return new ResolvedIdentity(userId, null, null, "NORMAL", "IDENTITY_AMBIGUOUS");
        }

        public static ResolvedIdentity invalid(UUID userId) {
            return new ResolvedIdentity(userId, null, null, "NORMAL", "IDENTITY_INVALID");
        }

        public boolean isError() { return errorCode != null; }
    }
}
