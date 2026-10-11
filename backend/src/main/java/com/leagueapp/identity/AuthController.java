package com.leagueapp.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AuthController {

	private final CurrentUser currentUser;
	private final IdentityService identityService;
	private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
	private final Environment environment;

	AuthController(CurrentUser currentUser, IdentityService identityService,
			ObjectProvider<ClientRegistrationRepository> clientRegistrations, Environment environment) {
		this.currentUser = currentUser;
		this.identityService = identityService;
		this.clientRegistrations = clientRegistrations;
		this.environment = environment;
	}

	@GetMapping("/api/me")
	IdentityService.UserSummary me() {
		return identityService.findUser(currentUser.requireId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
	}

	/** Sign-in options the frontend should show, e.g. ["google", "discord"] or ["dev"]. */
	@GetMapping("/api/public/auth/providers")
	List<String> providers() {
		List<String> providers = new ArrayList<>();
		if (clientRegistrations.getIfAvailable() instanceof Iterable<?> registrations) {
			registrations.forEach(r -> providers.add(((ClientRegistration) r).getRegistrationId()));
		}
		if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
			providers.add("dev");
		}
		return providers;
	}

}
