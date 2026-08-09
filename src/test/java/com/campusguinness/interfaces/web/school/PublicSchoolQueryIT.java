package com.campusguinness.interfaces.web.school;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
class PublicSchoolQueryIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String runPrefix = "phase11-public-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
    }

    @Test
    void anonymousSchoolListReturnsOnlyNormalSchoolsWithPublicFields() throws Exception {
        var normalId = insertSchool("normal", "NORMAL");
        var disabledId = insertSchool("disabled", "DISABLED");

        mvc.perform(get("/api/v1/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(hasItem(normalId.toString())))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem(disabledId.toString()))))
                .andExpect(jsonPath("$.items[*].name").value(hasItem(runPrefix + "-normal")))
                .andExpect(jsonPath("$.items[*].name").value(not(hasItem(runPrefix + "-disabled"))))
                .andExpect(jsonPath("$.items[*].schoolType").value(hasItem("PRIMARY")))
                .andExpect(jsonPath("$.items[*].region").value(hasItem("Beijing")))
                .andExpect(jsonPath("$.items[*]", everyItem(hasKey("id"))))
                .andExpect(jsonPath("$.items[*]", everyItem(hasKey("name"))))
                .andExpect(jsonPath("$.items[*]", everyItem(hasKey("schoolType"))))
                .andExpect(jsonPath("$.items[*]", everyItem(hasKey("region"))))
                .andExpect(jsonPath("$.items[*]", everyItem(not(hasKey("address")))))
                .andExpect(jsonPath("$.items[*]", everyItem(not(hasKey("contactName")))))
                .andExpect(jsonPath("$.items[*]", everyItem(not(hasKey("contactPhone")))))
                .andExpect(jsonPath("$.items[*]", everyItem(not(hasKey("contactEmail")))))
                .andExpect(jsonPath("$.items[*]", everyItem(not(hasKey("internalCode")))))
                .andExpect(jsonPath("$.items[*]", everyItem(not(hasKey("unifiedCode")))))
                .andExpect(jsonPath("$.items[*]", everyItem(not(hasKey("status")))));
    }

    @Test
    void anonymousSchoolDetailRemainsClosed() throws Exception {
        var normalId = insertSchool("detail", "NORMAL");

        mvc.perform(get("/api/v1/schools/" + normalId))
                .andExpect(status().isUnauthorized());
    }

    private UUID insertSchool(String label, String status) {
        var id = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-" + label, "USCC", "phase11-uc-" + suffix,
                "phase11-ic-" + suffix, "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", "phase11@example.com", status);
        return id;
    }
}
