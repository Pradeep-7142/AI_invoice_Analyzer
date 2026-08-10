package com.invoiceiq.repository;

import com.invoiceiq.entity.Budget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByOrganizationIdOrderByCategoryAsc(UUID organizationId);

    Optional<Budget> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Budget> findByOrganizationIdAndCategoryIgnoreCase(UUID organizationId, String category);
}
