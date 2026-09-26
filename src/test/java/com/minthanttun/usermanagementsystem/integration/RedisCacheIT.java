package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.admin.AdminUserService;
import com.minthanttun.usermanagementsystem.admin.dto.AdminUpdateUserRequest;
import com.minthanttun.usermanagementsystem.user.UserService;
import com.minthanttun.usermanagementsystem.user.dto.UpdateProfileRequest;
import com.minthanttun.usermanagementsystem.common.exception.DuplicateResourceException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;

public class RedisCacheIT extends IntegrationSupport {
    @Autowired
    UserService userService;

    @Autowired
    AdminUserService adminUserService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Test
    void userCacheServesStoredDtoUntilEvicted() {
        var u=user("cached");
        var first=userService.getCachedProfile(u.getId());

        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
        String key="users::"+u.getId();
        assertThat(stringRedisTemplate.hasKey(key)).isTrue();
        assertThat(stringRedisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1L,600L);
        // Bypass service eviction: the next read MUST still return the cached value.
        jdbcTemplate.update("update users set username='changed' where id=?",u.getId());
        assertThat(userService.getCachedProfile(u.getId())).isEqualTo(first);
        cacheManager.getCache("users").evict(u.getId());
        assertThat(userService.getCachedProfile(u.getId()).username()).isEqualTo("changed");
    }

    @Test
    void adminCacheRoundTripsTimestampAndNullableFields() {
        var u=user("adminCached");
        var first=adminUserService.getCachedUserResponse(u.getId());
        jdbcTemplate.update("update users set username='changed' where id=?",u.getId());
        // Second call deserializes the real Redis JSON, including OffsetDateTime fields.

        assertThat(adminUserService.getCachedUserResponse(u.getId())).isEqualTo(first);
        assertThat(first.createdAt()).isNotNull(); assertThat(first.lockedUntil()).isNull();
    }

    @Test
    void profileUpdateEvictsBothCachesThroughSpringProxy() {
        var u=user("before");
        userService.getCachedProfile(u.getId());
        adminUserService.getCachedUserResponse(u.getId());
        userService.updateProfile(u,new UpdateProfileRequest("after",null,null));

        assertBothAbsent(u.getId());
        assertThat(userService.getCachedProfile(u.getId()).username()).isEqualTo("after");
        assertThat(adminUserService.getCachedUserResponse(u.getId()).username()).isEqualTo("after");
    }

    @Test
    void adminUpdateEvictsBothCaches() {
        var u=user("before");
        var actor=user("actor");
        userService.getCachedProfile(u.getId());
        adminUserService.getCachedUserResponse(u.getId());
        adminUserService.updateUser(u.getId(),new AdminUpdateUserRequest("after",null,null),actor);

        assertBothAbsent(u.getId());
    }

    @Test
    void rejectedUpdateDoesNotReplaceCachedProfile() {
        var u=user("before");
        user("taken");
        var original=userService.getCachedProfile(u.getId());
        adminUserService.getCachedUserResponse(u.getId());

        assertThatThrownBy(() -> userService.updateProfile(u,new UpdateProfileRequest("taken",null,null)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(cacheManager.getCache("users").get(u.getId())).isNotNull();
        assertThat(cacheManager.getCache("adminUsers").get(u.getId())).isNotNull();
        assertThat(userService.getCachedProfile(u.getId())).isEqualTo(original);
        assertThat(userRepository.findById(u.getId()).orElseThrow().getUsername()).isEqualTo("before");
    }

    private void assertBothAbsent(java.util.UUID id) {
        assertThat(cacheManager.getCache("users").get(id)).isNull();
        assertThat(cacheManager.getCache("adminUsers").get(id)).isNull();
    }
}
