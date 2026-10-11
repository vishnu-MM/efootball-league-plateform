package com.leagueapp.identity;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the signed-in app user for the current request. */
@Component
public class CurrentUser {

	static final String SESSION_ATTRIBUTE = "appUserId";

	public Optional<UUID> id() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return Optional.empty();
		}
		HttpServletRequest request = attributes.getRequest();
		HttpSession session = request.getSession(false);
		if (session == null) {
			return Optional.empty();
		}
		return Optional.ofNullable((UUID) session.getAttribute(SESSION_ATTRIBUTE));
	}

	public UUID requireId() {
		return id().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
	}

}
