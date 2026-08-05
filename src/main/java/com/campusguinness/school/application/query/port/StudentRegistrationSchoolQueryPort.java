package com.campusguinness.school.application.query.port;

import com.campusguinness.school.application.query.model.StudentRegistrationSchool;

import java.util.UUID;

public interface StudentRegistrationSchoolQueryPort {

    StudentRegistrationSchool findForStudentRegistration(UUID schoolId);
}
