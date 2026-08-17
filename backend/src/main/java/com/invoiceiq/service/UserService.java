package com.invoiceiq.service;

import com.invoiceiq.audit.AuditLogService;
import com.invoiceiq.dto.CurrentUserResponse;
import com.invoiceiq.dto.UserSummaryDto;
import com.invoiceiq.entity.UserAccount;
import com.invoiceiq.entity.UserRole;
import com.invoiceiq.entity.UserStatus;
import com.invoiceiq.exception.ResourceNotFoundException;
import com.invoiceiq.repository.UserAccountRepository;
import com.invoiceiq.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;

    public UserService(
        UserAccountRepository userAccountRepository,
        CurrentUser currentUser,
        AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        UserAccount user = currentUser.entity();
        return new CurrentUserResponse(
            toDto(user),
            user.getRole()
        );
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> listUsers() {
        return userAccountRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public UserSummaryDto updateRole(UUID userId, UserRole role) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setRole(role);
        userAccountRepository.save(user);

        auditLogService.record(currentUser.entity(), "user.role_updated", "User", userId.toString(),
            Map.of("role", role.name()));

        return toDto(user);
    }

    @Transactional
    public UserSummaryDto toggleStatus(UUID userId) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setStatus(user.getStatus() == UserStatus.ACTIVE ? UserStatus.DISABLED : UserStatus.ACTIVE);
        userAccountRepository.save(user);

        auditLogService.record(currentUser.entity(), "user.status_toggled", "User", userId.toString(),
            Map.of("status", user.getStatus().name()));

        return toDto(user);
    }

    private UserSummaryDto toDto(UserAccount user) {
        return new UserSummaryDto(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getStatus()
        );
    }
}
