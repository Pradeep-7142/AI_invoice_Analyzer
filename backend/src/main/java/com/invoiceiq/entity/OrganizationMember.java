package com.invoiceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "organization_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "user_id"})
)
public class OrganizationMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrgRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    protected OrganizationMember() {
    }

    public OrganizationMember(Organization organization, UserAccount user, OrgRole role) {
        this.organization = organization;
        this.user = user;
        this.role = role;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UserAccount getUser() {
        return user;
    }

    public OrgRole getRole() {
        return role;
    }

    public void setRole(OrgRole role) {
        this.role = role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }
}
