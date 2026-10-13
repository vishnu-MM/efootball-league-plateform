package com.leagueapp.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.leagueapp.ApiSupport;
import com.leagueapp.ApiSupport.User;
import com.leagueapp.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class UploadsApiTests {

	/** The 8-byte PNG signature plus a little data: enough for type detection. */
	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4 };

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void cardProofCanBeRequiredAndOnlyTheManagerAndAdminsSeeIt() throws Exception {
		User owner = api.login("Upload Owner");
		User ann = api.login("Upload Ann");
		User ben = api.login("Upload Ben");
		String slug = api.createLeague(owner, "Upload Cup", "PUBLIC");
		api.join(owner, slug, ann, ben);

		upload(ann, slug, "CARD_PROOF", "notes.txt", "hello".getBytes()).andExpect(status().isBadRequest());
		api.send(put("/api/leagues/" + slug + "/squad-rules"), owner, "{\"capType\":\"NONE\",\"requireCardProof\":true}")
			.andExpect(status().isOk());
		api.get("/api/leagues/" + slug + "/roster", ann).andExpect(jsonPath("$.rules.requireCardProof").value(true));

		String card = "{\"playerName\":\"Pedri\",\"cardType\":\"STANDARD\",\"rating\":88,\"position\":\"CMF\"";
		api.send(post("/api/leagues/" + slug + "/squad/cards"), ann, card + "}").andExpect(status().isConflict());

		String proof = id(upload(ann, slug, "CARD_PROOF", "pedri.png", PNG).andExpect(status().isCreated()));
		String benProof = id(upload(ben, slug, "CARD_PROOF", "ben.png", PNG));
		api.send(post("/api/leagues/" + slug + "/squad/cards"), ann, card + ",\"proofUploadId\":\"" + benProof + "\"}")
			.andExpect(status().isBadRequest()); // someone else's screenshot
		String squadCard = id(api.send(post("/api/leagues/" + slug + "/squad/cards"), ann,
				card + ",\"proofUploadId\":\"" + proof + "\"}").andExpect(status().isCreated()));
		api.send(post("/api/leagues/" + slug + "/squad/cards"), ann,
				"{\"playerName\":\"Gavi\",\"cardType\":\"STANDARD\",\"rating\":85,\"position\":\"CMF\",\"proofUploadId\":\"" + proof + "\"}")
			.andExpect(status().isBadRequest()); // already used

		api.get("/api/leagues/" + slug + "/squad/approvals", owner).andExpect(jsonPath("$[0].hasProof").value(true));
		String proofPath = "/api/leagues/" + slug + "/squad/cards/" + squadCard + "/proof";
		api.get(proofPath, owner).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(PNG));
		api.get(proofPath, ann).andExpect(status().isOk());
		api.get(proofPath, ben).andExpect(status().isNotFound());
	}

	@Test
	void disputeEvidenceIsForThePlayersAndAdmins() throws Exception {
		User owner = api.login("Evidence Owner");
		User ann = api.login("Evidence Ann");
		User ben = api.login("Evidence Ben");
		User cat = api.login("Evidence Cat");
		String slug = api.createLeague(owner, "Evidence Cup", "PUBLIC");
		api.join(owner, slug, ann, ben, cat);
		api.send(post("/api/leagues/" + slug + "/seasons"), owner,
				"{\"participantIds\":[\"" + ann.id() + "\",\"" + ben.id() + "\"],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}")
			.andExpect(status().isCreated());
		Map<String, Object> fixture = JsonPath.read(api.get("/api/public/leagues/" + slug + "/season", ann)
			.andReturn().getResponse().getContentAsString(), "$.season.fixtures[0]");
		String fixtureId = (String) fixture.get("id");

		String shot = id(upload(ann, slug, "DISPUTE_EVIDENCE", "full-time.webp", webp()));
		String cardShot = id(upload(ann, slug, "CARD_PROOF", "card.png", PNG));
		api.send(post("/api/leagues/" + slug + "/fixtures/" + fixtureId + "/report"), ann,
				"{\"homeGoals\":1,\"awayGoals\":0,\"evidenceUploadIds\":[\"" + cardShot + "\"]}")
			.andExpect(status().isBadRequest()); // wrong purpose
		api.send(post("/api/leagues/" + slug + "/fixtures/" + fixtureId + "/report"), ann,
				"{\"homeGoals\":1,\"awayGoals\":0,\"evidenceUploadIds\":[\"" + shot + "\"]}")
			.andExpect(status().isNoContent());

		String detail = "/api/public/leagues/" + slug + "/fixtures/" + fixtureId;
		api.get(detail, ben).andExpect(jsonPath("$.reports[0].evidence[0]").value(shot));
		api.get(detail, cat).andExpect(jsonPath("$.reports[0].evidence.length()").value(0));
		api.get(detail, null).andExpect(jsonPath("$.reports[0].evidence.length()").value(0));

		String image = "/api/leagues/" + slug + "/fixtures/" + fixtureId + "/evidence/" + shot;
		api.get(image, ben).andExpect(status().isOk()).andExpect(content().contentType("image/webp"));
		api.get(image, owner).andExpect(status().isOk());
		api.get(image, cat).andExpect(status().isNotFound());
		api.get("/api/leagues/" + slug + "/fixtures/" + fixtureId + "/evidence/" + cardShot, owner)
			.andExpect(status().isNotFound()); // not attached to this match
		assertThat(JsonPath.<Integer>read(api.get(detail, ann).andReturn().getResponse().getContentAsString(),
				"$.reports.length()")).isEqualTo(1);
	}

	private ResultActions upload(User user, String slug, String purpose, String name, byte[] bytes) throws Exception {
		return mvc.perform(multipart("/api/leagues/" + slug + "/uploads")
			.file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
			.param("purpose", purpose)
			.cookie(user.session())
			.with(csrf()));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static byte[] webp() {
		byte[] b = new byte[16];
		System.arraycopy("RIFF".getBytes(), 0, b, 0, 4);
		System.arraycopy("WEBP".getBytes(), 0, b, 8, 4);
		return b;
	}

}
