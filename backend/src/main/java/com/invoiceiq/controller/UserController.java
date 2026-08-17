package com.invoiceiq.controller;

import com.invoiceiq.dto.CurrentUserResponse;
import com.invoiceiq.dto.UserSummaryDto;
import com.invoiceiq.entity.UserRole;
import com.invoiceiq.service.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/me")
    public CurrentUserResponse me() {
        return userService.getCurrentUser();
    }

    @GetMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserSummaryDto> listUsers() {
        return userService.listUsers();
    }

    @PostMapping("/api/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummaryDto updateRole(@PathVariable UUID userId, @RequestParam UserRole role) {
        return userService.updateRole(userId, role);
    }

    @PostMapping("/api/users/{userId}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummaryDto toggleStatus(@PathVariable UUID userId) {
        return userService.toggleStatus(userId);
    }
}
