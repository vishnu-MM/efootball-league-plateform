package com.leagueapp.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.leagueapp.ApiSupport;
import com.leagueapp.ApiSupport.User;
import com.leagueapp.TestcontainersConfiguration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class LeagueDeletionApiTests {

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4 };

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	LeaguePurger purger;

	@Value("${app.storage.endpoint}")
	String storageEndpoint;

	@Value("${app.storage.bucket}")
	String bucket;

	@Value("${app.storage.access-key}")
	String accessKey;

	@Value("${app.storage.secret-key}")
	String secretKey;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void ownerDeletesAndRestores() throws Exception {
		User owner = api.login("Delete Owner");
		User member = api.login("Delete Member");
		String slug = api.createLeague(owner, "Delete Cup", "PUBLIC");
		api.join(owner, slug, member);
		String code = JsonPath.read(api.send(post("/api/leagues/" + slug + "/invite"), owner, null)
			.andReturn().getResponse().getContentAsString(), "$.code");

		api.send(delete("/api/leagues/" + slug), member, "{\"confirmName\":\"Delete Cup\"}").andExpect(status().isForbidden());
		api.send(delete("/api/leagues/" + slug), owner, "{\"confirmName\":\"wrong\"}").andExpect(status().isBadRequest());
		api.send(delete("/api/leagues/" + slug), owner, "{\"confirmName\":\"Delete Cup\"}").andExpect(status().isNoContent());

		api.get("/api/public/leagues/" + slug, member).andExpect(status().isNotFound());
		api.get("/api/public/leagues", null).andExpect(jsonPath("$[*].slug", Matchers.not(Matchers.hasItem(slug))));
		api.get("/api/public/invites/" + code, member).andExpect(status().isNotFound());
		api.get("/api/leagues/mine", member)
			.andExpect(jsonPath("$[?(@.slug == '" + slug + "')].deletedAt", Matchers.hasSize(1)))
			.andExpect(jsonPath("$[?(@.slug == '" + slug + "')].deletedAt", Matchers.everyItem(Matchers.notNullValue())));
		api.get("/api/public/leagues/" + slug, owner)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.restoreUntil").isNotEmpty());

		api.send(post("/api/leagues/" + slug + "/restore"), member, null).andExpect(status().isNotFound());
		api.send(post("/api/leagues/" + slug + "/restore"), owner, null).andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug, member)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deletedAt").doesNotExist());
	}

	@Test
	void otherModulesHideADeletedLeague() throws Exception {
		User owner = api.login("Hidden Modules Owner");
		User member = api.login("Hidden Modules Member");
		String slug = api.createLeague(owner, "Hidden Modules Cup", "PUBLIC");
		api.join(owner, slug, member);
		api.send(post("/api/leagues/" + slug + "/seasons"), owner, "{\"participantIds\":[\"" + owner.id() + "\",\""
				+ member.id() + "\"],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}")
			.andExpect(status().isCreated());
		String fixtureId = JsonPath.read(api.get("/api/public/leagues/" + slug + "/season", owner)
			.andReturn().getResponse().getContentAsString(), "$.season.fixtures[0].id");

		api.send(delete("/api/leagues/" + slug), owner, "{\"confirmName\":\"Hidden Modules Cup\"}")
			.andExpect(status().isNoContent());

		api.get("/api/public/leagues/" + slug + "/fixtures/" + fixtureId, null).andExpect(status().isNotFound());
		api.get("/api/public/leagues/" + slug + "/log", null).andExpect(status().isNotFound());
		api.get("/api/public/leagues/" + slug + "/standings", member).andExpect(status().isNotFound());
	}

	@Test
	void restoreAfterSevenDaysIsRefused() throws Exception {
		User owner = api.login("Late Owner");
		String slug = api.createLeague(owner, "Late Cup", "PRIVATE");
		api.send(delete("/api/leagues/" + slug), owner, "{\"confirmName\":\"Late Cup\"}").andExpect(status().isNoContent());
		jdbc.update("update league set deleted_at = now() - interval '8 days' where slug = ?", slug);

		api.send(post("/api/leagues/" + slug + "/restore"), owner, null).andExpect(status().isConflict());
	}

	@Test
	void purgeRemovesTheLeagueAndItsUploads() throws Exception {
		User owner = api.login("Purge Owner");
		String slug = api.createLeague(owner, "Purge Cup", "PRIVATE");
		String uploadId = JsonPath.read(mvc.perform(multipart("/api/leagues/" + slug + "/uploads")
			.file(new MockMultipartFile("file", "proof.png", "application/octet-stream", PNG))
			.param("purpose", "CARD_PROOF")
			.cookie(owner.session())
			.with(csrf())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String key = jdbc.queryForObject("select storage_key from upload where id = ?", String.class, UUID.fromString(uploadId));
		try (S3Client s3 = s3()) {
			s3.headObject(b -> b.bucket(bucket).key(key)); // stored

			api.send(delete("/api/leagues/" + slug), owner, "{\"confirmName\":\"Purge Cup\"}").andExpect(status().isNoContent());
			jdbc.update("update league set deleted_at = now() - interval '8 days' where slug = ?", slug);
			purger.purgeExpired();

			assertThat(jdbc.queryForObject("select count(*) from league where slug = ?", Integer.class, slug)).isZero();
			assertThatThrownBy(() -> s3.headObject(b -> b.bucket(bucket).key(key))).isInstanceOf(NoSuchKeyException.class);
		}
	}

	@Test
	void membersAreNotifiedOfDeletion() throws Exception {
		User owner = api.login("Notice Owner");
		User member = api.login("Notice Member");
		String slug = api.createLeague(owner, "Notice Cup", "PRIVATE");
		api.join(owner, slug, member);

		api.send(delete("/api/leagues/" + slug), owner, "{\"confirmName\":\"Notice Cup\"}").andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(types(member)).contains("LEAGUE_DELETED"));
		assertThat(types(owner)).doesNotContain("LEAGUE_DELETED");
	}

	private List<String> types(User user) throws Exception {
		return JsonPath.read(api.get("/api/notifications", user).andReturn().getResponse().getContentAsString(), "$.items[*].type");
	}

	private S3Client s3() {
		return S3Client.builder()
			.endpointOverride(URI.create(storageEndpoint))
			.region(Region.US_EAST_1)
			.forcePathStyle(true)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
			.build();
	}

}
