package com.leagueapp.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityService {

	private final AppUserRepository users;
	private final UserIdentityRepository identities;

	IdentityService(AppUserRepository users, UserIdentityRepository identities) {
		this.users = users;
		this.identities = identities;
	}

	/**
	 * Returns the user linked to this provider account, creating the user on first sign-in.
	 * The email is only stored if no other account already uses it (accounts are never merged automatically).
	 */
	@Transactional
	UUID signIn(String provider, String subject, String displayName, String email) {
		return identities.findByProviderAndSubject(provider, subject)
			.map(UserIdentity::getUserId)
			.orElseGet(() -> {
				String storedEmail = (email != null && !users.existsByEmail(email)) ? email : null;
				AppUser user = users.save(new AppUser(cleanName(displayName), storedEmail));
				identities.save(new UserIdentity(user.getId(), provider, subject));
				return user.getId();
			});
	}

	public Optional<UserSummary> findUser(UUID userId) {
		return users.findById(userId).map(u -> new UserSummary(u.getId(), u.getDisplayName(), u.getEmail()));
	}

	private static String cleanName(String displayName) {
		String name = (displayName == null || displayName.isBlank()) ? "Player" : displayName.strip();
		return name.length() > AppUser.DISPLAY_NAME_MAX ? name.substring(0, AppUser.DISPLAY_NAME_MAX) : name;
	}

	public record UserSummary(UUID id, String displayName, String email) {
	}

}
