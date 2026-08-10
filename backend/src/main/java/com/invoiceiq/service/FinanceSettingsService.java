package com.invoiceiq.service;

import com.invoiceiq.dto.FinanceSettingsRequest;
import com.invoiceiq.dto.FinanceSettingsResponse;
import com.invoiceiq.entity.Organization;
import com.invoiceiq.exception.BusinessValidationException;
import com.invoiceiq.repository.OrganizationRepository;
import com.invoiceiq.security.CurrentUser;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Org-admin-only configuration for the two approval thresholds {@link ApprovalPolicy} reads. */
@Service
public class FinanceSettingsService {

    private final OrganizationRepository organizationRepository;
    private final CurrentUser currentUser;

    public FinanceSettingsService(OrganizationRepository organizationRepository, CurrentUser currentUser) {
        this.organizationRepository = organizationRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public FinanceSettingsResponse get() {
        Organization organization = organizationRepository.getReferenceById(currentUser.organizationId());
        return toResponse(organization);
    }

    @Transactional
    public FinanceSettingsResponse update(FinanceSettingsRequest request) {
        BigDecimal managerThreshold = request.managerApprovalThreshold();
        BigDecimal adminThreshold = request.adminApprovalThreshold();
        if (managerThreshold != null && adminThreshold != null && adminThreshold.compareTo(managerThreshold) < 0) {
            throw new BusinessValidationException(
                "The admin approval threshold must be greater than or equal to the manager approval threshold.");
        }

        Organization organization = organizationRepository.getReferenceById(currentUser.organizationId());
        organization.setManagerApprovalThreshold(managerThreshold);
        organization.setAdminApprovalThreshold(adminThreshold);

        return toResponse(organization);
    }

    private FinanceSettingsResponse toResponse(Organization organization) {
        return new FinanceSettingsResponse(organization.getManagerApprovalThreshold(), organization.getAdminApprovalThreshold());
    }
}
