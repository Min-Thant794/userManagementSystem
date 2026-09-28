package com.minthanttun.usermanagementsystem;

import com.minthanttun.usermanagementsystem.common.TokenCleanupJob;
import com.minthanttun.usermanagementsystem.config.SecurityConfig;
import com.minthanttun.usermanagementsystem.integration.IntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		classes = UsermanagementsystemApplication.class,
		properties = "spring.config.location=classpath:application-smoke.properties"
)
@ActiveProfiles("application-smoke")
class UsermanagementsystemApplicationTests {

	@Autowired
	ApplicationContext context;

	// Prevent scheduled cleanup from interfering with other tests.
	@MockitoBean
	TokenCleanupJob tokenCleanupJob;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(SecurityConfig.class))
				.hasSize(1);

		assertThat(context.getBeansOfType(IntegrationTestConfig.class))
				.isEmpty();
	}
}