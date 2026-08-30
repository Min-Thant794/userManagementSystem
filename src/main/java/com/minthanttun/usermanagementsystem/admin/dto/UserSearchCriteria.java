package com.minthanttun.usermanagementsystem.admin.dto;

import com.minthanttun.usermanagementsystem.user.AccountStatus;
import com.minthanttun.usermanagementsystem.user.Role;

public record UserSearchCriteria (
  String search,
  Role role,
  AccountStatus status
) {
}
