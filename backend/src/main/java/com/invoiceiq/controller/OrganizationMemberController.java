package com.invoiceiq.controller;

import com.invoiceiq.dto.CreateMemberRequest;
import com.invoiceiq.dto.MemberResponse;
import com.invoiceiq.dto.UpdateMemberRoleRequest;
import com.invoiceiq.service.OrganizationMemberService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/members")
@PreAuthorize("hasAuthority('ORGANIZATION_ADMIN')")
public class OrganizationMemberController {

    private final OrganizationMemberService organizationMemberService;

    public OrganizationMemberController(OrganizationMemberService organizationMemberService) {
        this.organizationMemberService = organizationMemberService;
    }

    @GetMapping
    public List<MemberResponse> list() {
        return organizationMemberService.listMembers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse add(@Valid @RequestBody CreateMemberRequest request) {
        return organizationMemberService.addMember(request);
    }

    @PatchMapping("/{membershipId}/role")
    public MemberResponse updateRole(@PathVariable UUID membershipId, @Valid @RequestBody UpdateMemberRoleRequest request) {
        return organizationMemberService.updateRole(membershipId, request);
    }

    @DeleteMapping("/{membershipId}")
    public ResponseEntity<Void> remove(@PathVariable UUID membershipId) {
        organizationMemberService.removeMember(membershipId);
        return ResponseEntity.noContent().build();
    }
}
