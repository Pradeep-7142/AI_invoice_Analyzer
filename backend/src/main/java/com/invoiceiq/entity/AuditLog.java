package com.invoiceiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserAccount actorUser;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    protected AuditLog() {
    }

    public AuditLog(Organization organization, UserAccount actorUser, String action,
                     String entityType, String entityId, Map<String, Object> metadata) {
        this.organization = organization;
        this.actorUser = actorUser;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadata;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UserAccount getActorUser() {
        return actorUser;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
