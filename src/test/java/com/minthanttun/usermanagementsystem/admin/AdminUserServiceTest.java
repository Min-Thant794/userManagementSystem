package com.minthanttun.usermanagementsystem.admin;

import com.minthanttun.usermanagementsystem.admin.dto.*;
import com.minthanttun.usermanagementsystem.audit.*;
import com.minthanttun.usermanagementsystem.auth.EmailVerificationService;
import com.minthanttun.usermanagementsystem.common.exception.*;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock UserRepository userRepository;
    @Mock AuditService audit;
    @Mock PasswordEncoder encoder;
    @Mock EmailVerificationService verification;
    @InjectMocks AdminUserService service;
    private User target;
    private User actor;

    @BeforeEach void setUp() {
        target = User.builder().id(UUID.randomUUID()).username("testuser").email("old@example.com")
                .phoneNumber("+6591111111").role(Role.USER).status(AccountStatus.ACTIVE).build();
        actor = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
    }
    private void existing() { when(userRepository.findById(target.getId())).thenReturn(Optional.of(target)); }
    private void saving() { when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0)); }

    @Test void getUserReturnsEntity() {
        existing();
        assertThat(service.getUser(target.getId())).isSameAs(target);
    }
    @Test void cachedReadMapsUserFields() {
        existing();
        AdminUserResponse response = service.getCachedUserResponse(target.getId());
        assertThat(response.id()).isEqualTo(target.getId());
        assertThat(response.username()).isEqualTo("testuser");
        assertThat(response.email()).isEqualTo("old@example.com");
        assertThat(response.role()).isEqualTo(Role.USER);
        // Direct invocation tests mapping, not Spring's caching interceptor.
    }
    @ParameterizedTest @ValueSource(strings = {"entity", "response", "update", "status", "role"})
    void missingUserIsRejected(String operation) {
        when(userRepository.findById(target.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> {
            switch (operation) {
                case "entity" -> service.getUser(target.getId());
                case "response" -> service.getCachedUserResponse(target.getId());
                case "update" -> service.updateUser(target.getId(), new AdminUpdateUserRequest("new", null, null), actor);
                case "status" -> service.updateStatus(target.getId(), AccountStatus.SUSPENDED, actor);
                case "role" -> service.updateRole(target.getId(), Role.ADMIN, actor);
                default -> throw new AssertionError(operation);
            }
        }).isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(audit, verification);
    }
    @Test void listingPassesPaginationAndReturnsRepositoryPage() {
        Pageable pageable = PageRequest.of(1, 20, Sort.by("username"));
        Page<User> page = new PageImpl<>(List.of(target), pageable, 21);
        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), eq(pageable))).thenReturn(page);
        assertThat(service.listUsers(new UserSearchCriteria("testuser", Role.USER, AccountStatus.ACTIVE), pageable))
                .isSameAs(page);
        verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), eq(pageable));
    }
    @Test void updateChangesFieldsRequestsVerificationAndAuditsBeforeAndAfter() {
        existing(); saving();
        User saved = service.updateUser(target.getId(),
                new AdminUpdateUserRequest("newname", "new@example.com", "+6592222222"), actor);
        assertThat(saved).isSameAs(target);
        assertThat(saved.getUsername()).isEqualTo("newname");
        assertThat(saved.getPhoneNumber()).isEqualTo("+6592222222");
        assertThat(saved.getEmail()).isEqualTo("old@example.com");
        assertThat(saved.getPendingEmail()).isEqualTo("new@example.com");
        verify(verification).generateVerificationEmail(target, "new@example.com");
        verify(audit).log(eq(actor.getId()), eq(target.getId()), eq(AuditAction.UPDATE), argThat(details -> {
            Map<?, ?> before = (Map<?, ?>) details.get("before");
            Map<?, ?> after = (Map<?, ?>) details.get("after");
            return "testuser".equals(before.get("username")) && "old@example.com".equals(before.get("email"))
                    && "+6591111111".equals(before.get("phoneNumber"))
                    && "newname".equals(after.get("username")) && "old@example.com".equals(after.get("email"))
                    && "+6592222222".equals(after.get("phoneNumber"));
        }));
    }
    @ParameterizedTest @ValueSource(strings = {"username", "email", "phone"})
    void duplicateUpdateIsRejectedWithoutSaveOrAudit(String field) {
        existing();
        AdminUpdateUserRequest request;
        switch (field) {
            case "username" -> { request = new AdminUpdateUserRequest("taken", null, null); when(userRepository.existsByUsername("taken")).thenReturn(true); }
            case "email" -> { request = new AdminUpdateUserRequest(null, "taken@example.com", null); when(userRepository.existsByEmail("taken@example.com")).thenReturn(true); }
            default -> { request = new AdminUpdateUserRequest(null, null, "+6592222222"); when(userRepository.existsByPhoneNumber("+6592222222")).thenReturn(true); }
        }
        assertThatThrownBy(() -> service.updateUser(target.getId(), request, actor)).isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(audit, verification);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nullOrUnchangedFieldsAvoidChecksAndAudit(boolean unchanged) {
        existing(); saving();
        AdminUpdateUserRequest request = unchanged
                ? new AdminUpdateUserRequest(target.getUsername(), target.getEmail(), target.getPhoneNumber())
                : new AdminUpdateUserRequest(null, null, null);
        service.updateUser(target.getId(), request, actor);
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).existsByPhoneNumber(anyString());
        verifyNoInteractions(audit, verification);
    }
    @Test void auditingHandlesPreviouslyNullProfileFields() {
        target.setUsername(null); target.setPhoneNumber(null);
        existing(); saving();
        service.updateUser(target.getId(), new AdminUpdateUserRequest("newname", null, "+6592222222"), actor);
        verify(audit).log(eq(actor.getId()), eq(target.getId()), eq(AuditAction.UPDATE), argThat(details ->
                ((Map<?, ?>) details.get("before")).get("username").equals("")
                        && ((Map<?, ?>) details.get("before")).get("phoneNumber").equals("")));
    }
    @ParameterizedTest @EnumSource(AccountStatus.class)
    void statusChangeIsSavedAndAudited(AccountStatus newStatus) {
        AccountStatus before = newStatus == AccountStatus.ACTIVE ? AccountStatus.SUSPENDED : AccountStatus.ACTIVE;
        target.setStatus(before); existing(); saving();
        assertThat(service.updateStatus(target.getId(), newStatus, actor).getStatus()).isEqualTo(newStatus);
        verify(audit).log(actor.getId(), target.getId(),
                newStatus == AccountStatus.SUSPENDED ? AuditAction.SUSPEND : AuditAction.REACTIVATE,
                Map.of("before", before.name(), "after", newStatus.name()));
        verify(userRepository, never()).countByRoleAndStatus(any(), any()); // This target is not an admin.
    }
    @ParameterizedTest @EnumSource(Role.class)
    void roleChangeIsSavedAndAudited(Role newRole) {
        Role before = newRole == Role.ADMIN ? Role.USER : Role.ADMIN;
        target.setRole(before); existing(); saving();
        if (before == Role.ADMIN) when(userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE)).thenReturn(2L);
        assertThat(service.updateRole(target.getId(), newRole, actor).getRole()).isEqualTo(newRole);
        verify(audit).log(actor.getId(), target.getId(), AuditAction.ROLE_CHANGE,
                Map.of("before", before.name(), "after", newRole.name()));
    }
    @ParameterizedTest @CsvSource({"suspend,0", "suspend,1", "demote,0", "demote,1"})
    void lastActiveAdminCannotBeRemoved(String action, long count) {
        target.setRole(Role.ADMIN); existing();
        when(userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE)).thenReturn(count);
        assertThatThrownBy(() -> {
            if (action.equals("suspend")) service.updateStatus(target.getId(), AccountStatus.SUSPENDED, actor);
            else service.updateRole(target.getId(), Role.USER, actor);
        }).isInstanceOf(LastAdminException.class);
        assertThat(target.getRole()).isEqualTo(Role.ADMIN);
        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository, never()).save(any()); verifyNoInteractions(audit);
    }
    @Test void adminCanBeSuspendedWhenAnotherActiveAdminRemains() {
        target.setRole(Role.ADMIN); existing(); saving();
        when(userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE)).thenReturn(2L);
        service.updateStatus(target.getId(), AccountStatus.SUSPENDED, actor);
        verify(userRepository).save(target);
        assertThat(target.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }
    @Test void suspendedAdminCanBeDemotedWithoutActiveAdminCountCheck() {
        target.setRole(Role.ADMIN); target.setStatus(AccountStatus.SUSPENDED); existing(); saving();
        service.updateRole(target.getId(), Role.USER, actor);
        assertThat(target.getRole()).isEqualTo(Role.USER);
        verify(userRepository, never()).countByRoleAndStatus(any(), any());
        verify(audit).log(eq(actor.getId()), eq(target.getId()), eq(AuditAction.ROLE_CHANGE), anyMap());
    }
    @Test void unchangedStatusAndRoleDoNotSaveOrAudit() {
        existing();
        assertThat(service.updateStatus(target.getId(), target.getStatus(), actor)).isSameAs(target);
        assertThat(service.updateRole(target.getId(), target.getRole(), actor)).isSameAs(target);
        verify(userRepository, never()).save(any()); verifyNoInteractions(audit);
    }
    @Test void createAdminHashesPasswordSetsRoleAndAudits() {
        UUID newId = UUID.randomUUID();
        when(encoder.encode("Password123!")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0); saved.setId(newId); return saved;
        });
        User result = service.createAdmin(new CreateAdminRequest("newadmin", "admin@example.com", "+6592222222", "Password123!"), actor);
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getPasswordHash()).isEqualTo("encoded");
        verify(audit).log(actor.getId(), newId, AuditAction.ADMIN_CREATED,
                Map.of("username", "newadmin", "email", "admin@example.com"));
    }
    @ParameterizedTest @ValueSource(strings = {"username", "email", "phone"})
    void duplicateAdminIsNotCreated(String field) {
        switch (field) {
            case "username" -> when(userRepository.existsByUsername("newadmin")).thenReturn(true);
            case "email" -> when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);
            default -> when(userRepository.existsByPhoneNumber("+6592222222")).thenReturn(true);
        }
        assertThatThrownBy(() -> service.createAdmin(new CreateAdminRequest("newadmin", "admin@example.com", "+6592222222", "Password123!"), actor))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any()); verifyNoInteractions(encoder, audit, verification);
    }
}