package com.campusguinness.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "student_profiles")
public class StudentProfileEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "grade", length = 32)
    private String grade;

    @Column(name = "class_name", length = 64)
    private String className;

    @Column(name = "student_number", length = 64)
    private String studentNumber;

    protected StudentProfileEntity() {}

    static StudentProfileEntity create(UUID id, UUID membershipId, String grade, String className, String studentNumber) {
        var entity = new StudentProfileEntity();
        entity.id = id;
        entity.membershipId = membershipId;
        entity.grade = grade;
        entity.className = className;
        entity.studentNumber = studentNumber;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getMembershipId() { return membershipId; }
    public String getGrade() { return grade; }
    public String getClassName() { return className; }
    public String getStudentNumber() { return studentNumber; }
}
