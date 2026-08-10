package com.invoiceiq.controller;

import com.invoiceiq.dto.CurrentUserResponse;
import com.invoiceiq.service.UserQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserQueryService userQueryService;

    public UserController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @GetMapping("/api/users/me")
    public CurrentUserResponse me() {
        return userQueryService.currentUser();
    }
}
