package com.leagueapp.identity;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** After Google/Discord sign-in: find or create the app user, remember it in the session, go to the app. */
@Component
class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

	private final IdentityService identityService;

	OAuthLoginSuccessHandler(IdentityService identityService) {
		this.identityService = identityService;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
		String provider = token.getAuthorizedClientRegistrationId();
		OAuth2User user = token.getPrincipal();
		Map<String, Object> attributes = user.getAttributes();

		UUID userId = identityService.signIn(provider, user.getName(), displayName(provider, attributes),
				verifiedEmail(provider, attributes));
		request.getSession().setAttribute(CurrentUser.SESSION_ATTRIBUTE, userId);
		response.sendRedirect("/");
	}

	private static String displayName(String provider, Map<String, Object> attributes) {
		if ("discord".equals(provider)) {
			Object globalName = attributes.get("global_name");
			return globalName != null ? globalName.toString() : (String) attributes.get("username");
		}
		return (String) attributes.get("name");
	}

	private static String verifiedEmail(String provider, Map<String, Object> attributes) {
		String verifiedKey = "discord".equals(provider) ? "verified" : "email_verified";
		return Boolean.TRUE.equals(attributes.get(verifiedKey)) ? (String) attributes.get("email") : null;
	}

}
