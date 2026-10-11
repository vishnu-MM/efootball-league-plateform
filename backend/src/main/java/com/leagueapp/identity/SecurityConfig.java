package com.leagueapp.identity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Session-cookie security for the PWA. API calls without a session get 401 (no redirects);
 * state-changing requests need the XSRF-TOKEN cookie echoed back as the X-XSRF-TOKEN header.
 */
@Configuration
class SecurityConfig {

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			ObjectProvider<ClientRegistrationRepository> clientRegistrations,
			OAuthLoginSuccessHandler oauthSuccessHandler) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				// /api/dev/** only exists when the "dev" profile is active.
				.requestMatchers("/api/public/**", "/api/dev/**", "/actuator/health/**").permitAll()
				.anyRequest().authenticated())
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.csrf(csrf -> csrf.spa())
			.logout(logout -> logout
				.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));

		// Google/Discord login is enabled only when at least one provider profile is active.
		if (clientRegistrations.getIfAvailable() != null) {
			http.oauth2Login(oauth -> oauth
				.successHandler(oauthSuccessHandler)
				.failureUrl("/?loginError"));
		}
		return http.build();
	}

}
