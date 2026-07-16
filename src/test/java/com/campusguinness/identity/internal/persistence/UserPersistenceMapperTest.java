package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("UserPersistenceMapper")
class UserPersistenceMapperTest {
    @Nested class AllStates {
        @ParameterizedTest @EnumSource(AccountStatus.class)
        void restoresState(AccountStatus s) {
            var e = entity(s.name());
            var u = UserPersistenceMapper.toDomain(e);
            assertThat(u.status()).isEqualTo(s);
            assertThat(u.domainEvents()).isEmpty();
        }
    }
    @Nested class Fields {
        @org.junit.jupiter.api.Test void keepsUsername() { var e=entity("NORMAL"); e.setUsername("testuser"); assertThat(UserPersistenceMapper.toDomain(e).username()).isEqualTo("testuser"); }
        @org.junit.jupiter.api.Test void keepsPlatformRole() { var e=entity("NORMAL"); e.setPlatformRole("SUPER_ADMIN"); assertThat(UserPersistenceMapper.toDomain(e).platformRole()).isEqualTo("SUPER_ADMIN"); }
    }
    @Nested class ToEntity {
        @org.junit.jupiter.api.Test void mapsToEntity() {
            var u = User.create(new User.Builder().id(new UserId(UUID.randomUUID())).username("test"));
            assertThat(UserPersistenceMapper.toEntity(u).getAccountStatus()).isEqualTo("PENDING_ACTIVATION");
        }
    }
    private UserEntity entity(String s) { var e=new UserEntity(); e.setId(UUID.randomUUID()); e.setUsername("u"); e.setAccountStatus(s); return e; }
}
