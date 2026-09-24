package com.rajmodi.snappark.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Drivers use the booking API anonymously (they arrive by scanning a QR code at the bay); operators
 * reach the admin API and metrics with HTTP Basic. Anything not listed is denied.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
		http
			// Stateless JSON API: no session cookie is ever issued, so there is nothing for CSRF to ride on.
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(withDefaults())
			.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
				.requestMatchers("/actuator/**", "/api/v1/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/v1/**").permitAll()
				.anyRequest().denyAll());
		return http.build();
	}

	@Bean
	UserDetailsService operators(SnapparkProperties properties) {
		SnapparkProperties.Admin admin = properties.admin();
		if (admin == null || !StringUtils.hasText(admin.passwordHash())) {
			// Secure default: with no configured hash there is no admin account at all.
			return new InMemoryUserDetailsManager();
		}
		return new InMemoryUserDetailsManager(User.withUsername(admin.username())
			.password(admin.passwordHash())
			.roles("ADMIN")
			.build());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
