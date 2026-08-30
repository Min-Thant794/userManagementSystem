package com.minthanttun.usermanagementsystem.admin;

import com.minthanttun.usermanagementsystem.user.AccountStatus;
import com.minthanttun.usermanagementsystem.user.Role;
import com.minthanttun.usermanagementsystem.user.User;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("role"), role);
    }

    public static Specification<User> hasStatus(AccountStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<User> matchesSearchTerm(String search) {
        return ((root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(root.get("phoneNumber"), pattern)
            );
        });
    }
}
