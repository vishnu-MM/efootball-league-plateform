package com.leagueapp.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	boolean existsByEmail(String email);

}

interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

	Optional<UserIdentity> findByProviderAndSubject(String provider, String subject);

}
