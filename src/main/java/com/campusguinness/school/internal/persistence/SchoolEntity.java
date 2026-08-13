package com.campusguinness.school.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schools")
public class SchoolEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Column(name = "unified_code_type", nullable = false, length = 32) private String unifiedCodeType;
    @Column(name = "unified_code", length = 64) private String unifiedCode;
    @Column(name = "internal_code", nullable = false, length = 32, unique = true) private String internalCode;
    @Column(name = "school_type", nullable = false, length = 32) private String schoolType;
    @Column(name = "region", nullable = false, length = 128) private String region;
    @Column(name = "address", nullable = false, columnDefinition = "text") private String address;
    @Column(name = "contact_name", nullable = false, length = 100) private String contactName;
    @Column(name = "contact_phone", nullable = false, length = 32) private String contactPhone;
    @Column(name = "contact_email", nullable = false, length = 200) private String contactEmail;
    @Column(name = "school_status", nullable = false, length = 32) private String schoolStatus;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;

    protected SchoolEntity() {}

    // ── package-private setters (for PersistenceMapper only) ──
    void setId(UUID v) { id = v; }
    void setName(String v) { name = v; }
    void setUnifiedCodeType(String v) { unifiedCodeType = v; }
    void setUnifiedCode(String v) { unifiedCode = v; }
    void setInternalCode(String v) { internalCode = v; }
    void setSchoolType(String v) { schoolType = v; }
    void setRegion(String v) { region = v; }
    void setAddress(String v) { address = v; }
    void setContactName(String v) { contactName = v; }
    void setContactPhone(String v) { contactPhone = v; }
    void setContactEmail(String v) { contactEmail = v; }
    void setSchoolStatus(String v) { schoolStatus = v; }
    void setCreatedAt(Instant v) { createdAt = v; }
    void setUpdatedAt(Instant v) { updatedAt = v; }
    void setVersion(int v) { version = v; }

    // ── public getters (read-only) ──
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getUnifiedCodeType() { return unifiedCodeType; }
    public String getUnifiedCode() { return unifiedCode; }
    public String getInternalCode() { return internalCode; }
    public String getSchoolType() { return schoolType; }
    public String getRegion() { return region; }
    public String getAddress() { return address; }
    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public String getSchoolStatus() { return schoolStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
