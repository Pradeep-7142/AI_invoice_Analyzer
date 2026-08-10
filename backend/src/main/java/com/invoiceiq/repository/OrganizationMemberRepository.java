package com.invoiceiq.repository;

import com.invoiceiq.entity.MembershipStatus;
import com.invoiceiq.entity.OrgRole;
import com.invoiceiq.entity.OrganizationMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    List<OrganizationMember> findByOrganizationIdAndStatusOrderByCreatedAtAsc(UUID organizationId, MembershipStatus status);

    Optional<OrganizationMember> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, OrgRole role, MembershipStatus status);
}
