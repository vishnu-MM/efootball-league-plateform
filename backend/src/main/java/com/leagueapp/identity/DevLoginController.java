package com.leagueapp.identity;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Name-only sign-in for local development and tests. Only exists with the "dev" profile. */
@Profile("dev")
@RestController
class DevLoginController {

	private final IdentityService identityService;
	private final SecurityContextRepository securityContextRepository;

	DevLoginController(IdentityService identityService, SecurityContextRepository securityContextRepository) {
		this.identityService = identityService;
		this.securityContextRepository = securityContextRepository;
	}

	@PostMapping("/api/dev/login")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void login(@Valid @RequestBody DevLoginRequest body, HttpServletRequest request, HttpServletResponse response) {
		String name = body.displayName().strip();
		String subject = name.toLowerCase(Locale.ROOT);
		UUID userId = identityService.signIn("dev", subject, name, null);

		// Prevent session fixation, then store the authentication and app user id in the session.
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("dev:" + subject, null,
				List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		request.getSession().setAttribute(CurrentUser.SESSION_ATTRIBUTE, userId);
	}

	record DevLoginRequest(@NotBlank @Size(max = 50) String displayName) {
	}

}
