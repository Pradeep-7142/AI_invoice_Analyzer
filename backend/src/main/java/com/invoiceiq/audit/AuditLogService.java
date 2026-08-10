package com.invoiceiq.audit;

import com.invoiceiq.dto.AuditLogResponse;
import com.invoiceiq.entity.AuditLog;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.repository.AuditLogRepository;
import com.invoiceiq.security.CurrentUser;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUser currentUser;

    public AuditLogService(AuditLogRepository auditLogRepository, CurrentUser currentUser) {
        this.auditLogRepository = auditLogRepository;
        this.currentUser = currentUser;
    }

    public void record(Organization organization, UserAccount actor, String action, String entityType, String entityId, Map<String, Object> metadata) {
        auditLogRepository.save(new AuditLog(organization, actor, action, entityType, entityId, metadata));
    }

    public Page<AuditLogResponse> listForCurrentOrganization(Pageable pageable) {
        return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(currentUser.organizationId(), pageable)
            .map(log -> new AuditLogResponse(
                log.getId(),
                log.getActorUser() != null ? log.getActorUser().getEmail() : null,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getMetadata(),
                log.getCreatedAt()
            ));
    }
}
