package com.campusguinness.school.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * School aggregate root.
 *
 * <p>State machine (CG-SCHOOL-001~009):
 * <pre>
 *   PENDING_ENABLE → NORMAL ⇄ SUSPENDED
 *                       ↓            ↓
 *                     DISABLED ←─────┘
 *                       ↓
 *                 PENDING_ENABLE
 * </pre>
 *
 * <p>Created from an approved SchoolRegistration. Managed exclusively by super admin.
 */
public final class School {

    private final SchoolId id;
    private final String name;
    private final String unifiedCodeType;
    private final String unifiedCode;
    private final String internalCode;
    private final String schoolType;
    private final String region;
    private final String address;
    private final String contactName;
    private final String contactPhone;
    private final String contactEmail;
    private SchoolStatus status;
    private final List<Object> domainEvents;

    private School(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.unifiedCodeType = b.unifiedCodeType;
        this.unifiedCode = b.unifiedCode;
        this.internalCode = b.internalCode;
        this.schoolType = b.schoolType;
        this.region = b.region;
        this.address = b.address;
        this.contactName = b.contactName;
        this.contactPhone = b.contactPhone;
        this.contactEmail = b.contactEmail;
        this.status = b.status != null ? b.status : SchoolStatus.PENDING_ENABLE;
        this.domainEvents = new ArrayList<>();
    }

    /** Create a new School in PENDING_ENABLE status. */
    public static School create(Builder builder) {
        validate(builder);
        return new School(builder);
    }

    /** Reconstitute from persistence — takes final status, no domain events. */
    public static School reconstitute(Builder builder) {
        validate(builder);
        if (builder.status == null) throw new IllegalArgumentException("status required for reconstitute");
        return new School(builder);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.name == null || b.name.isBlank()) throw new IllegalArgumentException("name required");
        if (b.name.length() > 200) throw new IllegalArgumentException("name max 200 chars");
        if (b.unifiedCodeType == null || b.unifiedCodeType.isBlank()) throw new IllegalArgumentException("unifiedCodeType required");
        if (b.internalCode == null || b.internalCode.isBlank()) throw new IllegalArgumentException("internalCode required");
        if (b.schoolType == null || b.schoolType.isBlank()) throw new IllegalArgumentException("schoolType required");
        if (b.region == null || b.region.isBlank()) throw new IllegalArgumentException("region required");
        if (b.address == null || b.address.isBlank()) throw new IllegalArgumentException("address required");
        if (b.contactName == null || b.contactName.isBlank()) throw new IllegalArgumentException("contactName required");
        if (b.contactPhone == null || b.contactPhone.isBlank()) throw new IllegalArgumentException("contactPhone required");
        if (b.contactEmail == null || b.contactEmail.isBlank()) throw new IllegalArgumentException("contactEmail required");
    }

    // ── State transitions ──

    /** CG-SCHOOL-001: PENDING_ENABLE → NORMAL */
    public void activate() {
        if (status != SchoolStatus.PENDING_ENABLE) {
            throw new InvalidSchoolStateTransitionException(status, "activate");
        }
        this.status = SchoolStatus.NORMAL;
        domainEvents.add(new SchoolActivated(id));
    }

    /** CG-SCHOOL-002: NORMAL → SUSPENDED */
    public void suspend(String reason) {
        if (status != SchoolStatus.NORMAL) {
            throw new InvalidSchoolStateTransitionException(status, "suspend");
        }
        this.status = SchoolStatus.SUSPENDED;
        domainEvents.add(new SchoolSuspended(id, reason));
    }

    /** CG-SCHOOL-004: SUSPENDED → NORMAL */
    public void restore() {
        if (status != SchoolStatus.SUSPENDED) {
            throw new InvalidSchoolStateTransitionException(status, "restore");
        }
        this.status = SchoolStatus.NORMAL;
        domainEvents.add(new SchoolRestored(id));
    }

    /** CG-SCHOOL-003/005: NORMAL/SUSPENDED → DISABLED */
    public void disable(String reason) {
        if (status != SchoolStatus.NORMAL && status != SchoolStatus.SUSPENDED) {
            throw new InvalidSchoolStateTransitionException(status, "disable");
        }
        this.status = SchoolStatus.DISABLED;
        domainEvents.add(new SchoolDisabled(id, reason));
    }

    /** CG-SCHOOL-006: DISABLED → PENDING_ENABLE */
    public void reEnable() {
        if (status != SchoolStatus.DISABLED) {
            throw new InvalidSchoolStateTransitionException(status, "re-enable");
        }
        this.status = SchoolStatus.PENDING_ENABLE;
        domainEvents.add(new SchoolReEnabled(id));
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public SchoolId id() { return id; }
    public String name() { return name; }
    public String unifiedCodeType() { return unifiedCodeType; }
    public String unifiedCode() { return unifiedCode; }
    public String internalCode() { return internalCode; }
    public String schoolType() { return schoolType; }
    public String region() { return region; }
    public String contactName() { return contactName; }
    public SchoolStatus status() { return status; }
    public String address() { return address; }
    public String contactPhone() { return contactPhone; }
    public String contactEmail() { return contactEmail; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    /** Builder */
    public static class Builder {
        private SchoolId id;
        private String name, unifiedCodeType, unifiedCode, internalCode, schoolType, region, address;
        private String contactName, contactPhone, contactEmail;
        SchoolStatus status;

        public Builder id(SchoolId v) { this.id = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder unifiedCodeType(String v) { this.unifiedCodeType = v; return this; }
        public Builder unifiedCode(String v) { this.unifiedCode = v; return this; }
        public Builder internalCode(String v) { this.internalCode = v; return this; }
        public Builder schoolType(String v) { this.schoolType = v; return this; }
        public Builder region(String v) { this.region = v; return this; }
        public Builder address(String v) { this.address = v; return this; }
        public Builder contactName(String v) { this.contactName = v; return this; }
        public Builder contactPhone(String v) { this.contactPhone = v; return this; }
        public Builder contactEmail(String v) { this.contactEmail = v; return this; }
        public Builder status(SchoolStatus v) { this.status = v; return this; }
    }
}
