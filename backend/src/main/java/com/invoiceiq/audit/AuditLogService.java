package com.invoiceiq.audit;

import com.invoiceiq.dto.AuditLogResponse;
import com.invoiceiq.entity.AuditLog;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.repository.AuditLogRepository;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(UserAccount actor, String action, String entityType, String entityId, Map<String, Object> metadata) {
        auditLogRepository.save(new AuditLog(actor, action, entityType, entityId, metadata));
    }

    public Page<AuditLogResponse> listLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
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
