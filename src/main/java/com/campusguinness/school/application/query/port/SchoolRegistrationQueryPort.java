package com.campusguinness.school.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolRegistrationDetailResult;
import com.campusguinness.school.application.query.model.SchoolRegistrationListResult;

import java.util.Optional;
import java.util.UUID;

public interface SchoolRegistrationQueryPort {

    QueryPage<SchoolRegistrationListResult> findAll(String status, int page, int size);

    Optional<SchoolRegistrationDetailResult> findById(UUID id);
}
