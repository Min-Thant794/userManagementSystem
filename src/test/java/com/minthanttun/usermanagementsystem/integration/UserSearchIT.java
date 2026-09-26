package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.admin.AdminUserService;
import com.minthanttun.usermanagementsystem.admin.dto.UserSearchCriteria;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import static org.assertj.core.api.Assertions.*;

class UserSearchIT extends IntegrationSupport {
    @Autowired AdminUserService adminUserService;

    @ParameterizedTest @ValueSource(strings = {"TESTUSER", "example.com", "1234567"})
    void searchesUsernameEmailAndPhone(String query) {
        var testuser = user("testuser");
        testuser.setPhoneNumber("+651234567");
        userRepository.saveAndFlush(testuser);

        assertThat(adminUserService.listUsers(new UserSearchCriteria(query, null, null), Pageable.unpaged())
                .getContent()).extracting(User::getId).containsExactly(testuser.getId());
    }

    @Test
    void combinesSearchRoleAndStatus() {
        var active = user("testuserActive");
        active.setRole(Role.ADMIN);

        userRepository.saveAndFlush(active);
        var suspended = user("testuserSuspended");
        suspended.setRole(Role.ADMIN);
        suspended.setStatus(AccountStatus.SUSPENDED);
        userRepository.saveAndFlush(suspended);
        user("testuserUser");

        assertThat(adminUserService.listUsers(new UserSearchCriteria("testuser", Role.ADMIN, AccountStatus.ACTIVE),
                Pageable.unpaged()).getContent()).extracting(User::getId).containsExactly(active.getId());
    }

    @Test
    void blankSearchSupportsStablePaginationAndTotalCount() {
        user("charlie");
        user("alice");
        user("bob");

        var result = adminUserService.listUsers(new UserSearchCriteria("  ", null, null),
                PageRequest.of(1, 2, Sort.by("username")));

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).extracting(User::getUsername).containsExactly("charlie");
    }

    @Test
    void nonMatchingSearchIsEmpty() {
        user("testuser");

        assertThat(adminUserService.listUsers(new UserSearchCriteria("absent", null, null),
                Pageable.unpaged())).isEmpty();
    }
}