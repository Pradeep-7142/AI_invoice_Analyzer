package com.invoiceiq.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.entity.Organization;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ApprovalPolicyTest {

    @Test
    void noThresholdsConfiguredMeansNoApprovalRequired() {
        Organization organization = new Organization("Acme", "acme");

        assertThat(ApprovalPolicy.requiredApproverRole(organization, new BigDecimal("999999"))).isNull();
    }

    @Test
    void amountBelowManagerThresholdRequiresNoApproval() {
        Organization organization = new Organization("Acme", "acme");
        organization.setManagerApprovalThreshold(new BigDecimal("10000"));

        assertThat(ApprovalPolicy.requiredApproverRole(organization, new BigDecimal("9999.99"))).isNull();
    }

    @Test
    void amountAtOrAboveManagerThresholdRequiresFinanceManager() {
        Organization organization = new Organization("Acme", "acme");
        organization.setManagerApprovalThreshold(new BigDecimal("10000"));

        assertThat(ApprovalPolicy.requiredApproverRole(organization, new BigDecimal("10000"))).isEqualTo(OrgRole.FINANCE_MANAGER);
        assertThat(ApprovalPolicy.requiredApproverRole(organization, new BigDecimal("50000"))).isEqualTo(OrgRole.FINANCE_MANAGER);
    }

    @Test
    void amountAtOrAboveAdminThresholdRequiresOrganizationAdminEvenWithALowerManagerThreshold() {
        Organization organization = new Organization("Acme", "acme");
        organization.setManagerApprovalThreshold(new BigDecimal("10000"));
        organization.setAdminApprovalThreshold(new BigDecimal("100000"));

        assertThat(ApprovalPolicy.requiredApproverRole(organization, new BigDecimal("100000"))).isEqualTo(OrgRole.ORGANIZATION_ADMIN);
        assertThat(ApprovalPolicy.requiredApproverRole(organization, new BigDecimal("50000"))).isEqualTo(OrgRole.FINANCE_MANAGER);
    }

    @Test
    void nullInvoiceAmountIsTreatedAsZero() {
        Organization organization = new Organization("Acme", "acme");
        organization.setManagerApprovalThreshold(new BigDecimal("0.00"));

        assertThat(ApprovalPolicy.requiredApproverRole(organization, null)).isEqualTo(OrgRole.FINANCE_MANAGER);
    }
}
