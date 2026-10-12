package com.leagueapp.roster;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
class RosterApiTests {

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void managersAddCardsAndAdminsApproveThem() throws Exception {
		User owner = api.login("Squad Owner");
		User ann = api.login("Squad Ann");
		String slug = api.createLeague(owner, "Squad Cup", "PRIVATE");
		api.join(owner, slug, ann);

		String annMessi = id(addNew(slug, ann, "  Lionel   Messi ", "EPIC", 98, "RWF")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.card.playerName").value("Lionel Messi")));

		// Unapproved cards don't show up in search.
		api.get(searchPath(slug, "mess"), owner).andExpect(jsonPath("$.length()").value(0));

		api.get(base(slug) + "/squad/approvals", ann).andExpect(status().isForbidden());
		api.get(base(slug) + "/squad/approvals", owner)
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].userId").value(ann.id()));
		api.get(base(slug) + "/roster", owner).andExpect(jsonPath("$.pendingCount").value(1));

		api.send(post(cardPath(slug, annMessi) + "/approve"), ann, null).andExpect(status().isForbidden());
		api.send(post(cardPath(slug, annMessi) + "/approve"), owner, null).andExpect(status().isNoContent());
		api.send(post(cardPath(slug, annMessi) + "/approve"), owner, null).andExpect(status().isConflict());

		// Now it's in the catalogue: the owner picks it from search rather than typing it again.
		String catalogueId = JsonPath.read(api.get(searchPath(slug, "MESS"), owner)
			.andExpect(jsonPath("$.length()").value(1))
			.andReturn().getResponse().getContentAsString(), "$[0].id");
		api.send(post(base(slug) + "/squad/cards"), owner, "{\"cardId\":\"" + catalogueId + "\"}")
			.andExpect(status().isCreated());
		// Typing the same card again (any case) reuses the catalogue entry, so it's a duplicate for Ann.
		addNew(slug, ann, "lionel messi", "EPIC", 98, "RWF").andExpect(status().isConflict());

		api.get(base(slug) + "/roster", ann)
			.andExpect(jsonPath("$.mySquad.size").value(1))
			.andExpect(jsonPath("$.mySquad.cards[0].status").value("APPROVED"))
			.andExpect(jsonPath("$.pendingCount").doesNotExist())
			.andExpect(jsonPath("$.windowOpen").value(true));
		api.get("/api/public/leagues/" + slug + "/squads/" + ann.id(), owner)
			.andExpect(jsonPath("$.cards.length()").value(1))
			.andExpect(jsonPath("$.cards[0].playerName").value("Lionel Messi"));
		// Private league: outsiders can't see squads.
		api.get("/api/public/leagues/" + slug + "/squads/" + ann.id(), null).andExpect(status().isNotFound());
	}

	@Test
	void rejectedCardsShowTheReasonAndCanBeCleared() throws Exception {
		User owner = api.login("Reject Owner");
		User ann = api.login("Reject Ann");
		String slug = api.createLeague(owner, "Reject Cup", "PUBLIC");
		api.join(owner, slug, ann);

		String card = id(addNew(slug, ann, "Fake Star", "LEGEND", 105, "CF"));
		api.send(post(cardPath(slug, card) + "/reject"), owner, "{\"reason\":\"No such card\"}")
			.andExpect(status().isNoContent());
		api.get(base(slug) + "/roster", ann)
			.andExpect(jsonPath("$.mySquad.cards[0].status").value("REJECTED"))
			.andExpect(jsonPath("$.mySquad.cards[0].rejectReason").value("No such card"))
			.andExpect(jsonPath("$.mySquad.size").value(0));

		// Clearing a rejected card works even while the window is closed.
		api.send(put(base(slug) + "/transfer-window"), owner, "{\"open\":false}").andExpect(status().isNoContent());
		api.send(delete(cardPath(slug, card)), owner, null).andExpect(status().isNotFound());
		api.send(delete(cardPath(slug, card)), ann, null).andExpect(status().isNoContent());
		api.get(base(slug) + "/roster", ann).andExpect(jsonPath("$.mySquad.cards.length()").value(0));
	}

	@Test
	void rulesBlockAddsAndTighterRulesFlagSquads() throws Exception {
		User owner = api.login("Rules Owner2");
		User ann = api.login("Rules Ann");
		String slug = api.createLeague(owner, "Squad Rules Cup", "PUBLIC");
		api.join(owner, slug, ann);

		saveRules(slug, ann, "{\"maxSquadSize\":2,\"capType\":\"PER_CARD\",\"capValue\":95,\"bannedCardTypes\":[\"EPIC\"]}")
			.andExpect(status().isForbidden());
		saveRules(slug, owner, "{\"capType\":\"SQUAD_TOTAL\"}").andExpect(status().isBadRequest());
		saveRules(slug, owner, "{\"maxSquadSize\":2,\"capType\":\"PER_CARD\",\"capValue\":95,\"bannedCardTypes\":[\"EPIC\"]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flagged.length()").value(0));

		addNew(slug, ann, "Too Good", "STANDARD", 96, "CF").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("limit per card is 95")));
		addNew(slug, ann, "Epic Guy", "EPIC", 90, "CF").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("Epic cards are banned")));
		String first = id(addNew(slug, ann, "Player One", "STANDARD", 90, "CF"));
		addNew(slug, ann, "Player Two", "STANDARD", 90, "GK").andExpect(status().isCreated());
		addNew(slug, ann, "Player Three", "STANDARD", 90, "CB").andExpect(status().isConflict());

		// A tighter size limit: the owner sees who it would flag, saves anyway, and Ann's squad is flagged.
		String tighter = "{\"maxSquadSize\":1,\"capType\":\"NONE\"}";
		api.send(post(base(slug) + "/squad-rules/check"), owner, tighter)
			.andExpect(jsonPath("$.flagged.length()").value(1))
			.andExpect(jsonPath("$.flagged[0].userId").value(ann.id()));
		saveRules(slug, owner, tighter).andExpect(jsonPath("$.flagged[0].name").value("Rules Ann"));
		api.get(base(slug) + "/roster", owner)
			.andExpect(jsonPath("$.rules.maxSquadSize").value(1))
			.andExpect(jsonPath("$.rules.capValue").doesNotExist())
			.andExpect(jsonPath("$.flagged.length()").value(1));
		api.get(base(slug) + "/roster", ann).andExpect(jsonPath("$.mySquad.problems[0]").value("2 cards in the squad; the limit is 1"));

		// A flagged squad can only shrink.
		addNew(slug, ann, "Player Four", "STANDARD", 70, "CB").andExpect(status().isConflict());
		api.send(delete(cardPath(slug, first)), ann, null).andExpect(status().isNoContent());
		api.get(base(slug) + "/roster", ann).andExpect(jsonPath("$.mySquad.problems.length()").value(0));
	}

	@Test
	void topElevenCapUsageIsReported() throws Exception {
		User owner = api.login("Cap Owner");
		String slug = api.createLeague(owner, "Cap Cup", "PUBLIC");
		saveRules(slug, owner, "{\"capType\":\"TOP_11_TOTAL\",\"capValue\":1000}").andExpect(status().isOk());
		addNew(slug, owner, "Keeper", "STANDARD", 80, "GK").andExpect(status().isCreated());
		addNew(slug, owner, "Striker", "STANDARD", 85, "CF").andExpect(status().isCreated());
		api.get(base(slug) + "/roster", owner)
			.andExpect(jsonPath("$.mySquad.capUsage").value(165))
			.andExpect(jsonPath("$.isOwner").value(true));
	}

	@Test
	void cardInputIsValidated() throws Exception {
		User owner = api.login("Input Owner");
		User outsider = api.login("Input Outsider");
		String slug = api.createLeague(owner, "Input Cup", "PUBLIC");

		addNew(slug, owner, "Too High", "STANDARD", 120, "CF").andExpect(status().isBadRequest());
		addNew(slug, owner, "X", "STANDARD", 80, "CF").andExpect(status().isBadRequest());
		api.send(post(base(slug) + "/squad/cards"), owner, "{\"playerName\":\"No Type\",\"rating\":80}")
			.andExpect(status().isBadRequest());
		api.send(post(base(slug) + "/squad/cards"), owner, "{\"cardId\":\"00000000-0000-0000-0000-000000000000\"}")
			.andExpect(status().isNotFound());
		addNew(slug, outsider, "Gate Crasher", "STANDARD", 80, "CF").andExpect(status().isForbidden());
	}

	@Test
	void theWindowFollowsSeasonsAndAdminsCanToggleIt() throws Exception {
		User owner = api.login("Window Owner");
		User ann = api.login("Window Ann");
		String slug = api.createLeague(owner, "Window Cup", "PUBLIC");
		api.join(owner, slug, ann);

		api.send(put(base(slug) + "/transfer-window"), ann, "{\"open\":false}").andExpect(status().isForbidden());
		api.send(put(base(slug) + "/transfer-window"), owner, "{\"open\":false}").andExpect(status().isNoContent());
		addNew(slug, ann, "Late Signing", "STANDARD", 80, "CF").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("transfer window is closed")));
		api.send(put(base(slug) + "/transfer-window"), owner, "{\"open\":true}").andExpect(status().isNoContent());
		addNew(slug, ann, "Late Signing", "STANDARD", 80, "CF").andExpect(status().isCreated());

		// A flagged squad blocks the season from starting.
		saveRules(slug, owner, "{\"capType\":\"PER_CARD\",\"capValue\":75}").andExpect(status().isOk());
		startSeason(slug, owner, ann).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("Window Ann")));
		saveRules(slug, owner, "{\"capType\":\"NONE\"}").andExpect(status().isOk());

		// Starting the season closes the window; finishing it opens the window again.
		startSeason(slug, owner, ann).andExpect(status().isCreated());
		api.get(base(slug) + "/roster", ann).andExpect(jsonPath("$.windowOpen").value(false));
		String fixture = JsonPath.read(api.get("/api/public/leagues/" + slug + "/season", ann)
			.andReturn().getResponse().getContentAsString(), "$.season.fixtures[0].id");
		api.send(post(base(slug) + "/fixtures/" + fixture + "/report"), ann, "{\"homeGoals\":1,\"awayGoals\":1}")
			.andExpect(status().isNoContent());
		api.send(post(base(slug) + "/fixtures/" + fixture + "/confirm"), owner, null).andExpect(status().isNoContent());
		api.get(base(slug) + "/roster", ann).andExpect(jsonPath("$.windowOpen").value(true));
	}

	private ResultActions startSeason(String slug, User owner, User other) throws Exception {
		return api.send(post(base(slug) + "/seasons"), owner, "{\"participantIds\":[\"" + owner.id() + "\",\"" + other.id()
				+ "\"],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}");
	}

	private ResultActions addNew(String slug, User user, String name, String type, int rating, String position)
			throws Exception {
		return api.send(post(base(slug) + "/squad/cards"), user, "{\"playerName\":\"" + name + "\",\"cardType\":\"" + type
				+ "\",\"rating\":" + rating + ",\"position\":\"" + position + "\"}");
	}

	private ResultActions saveRules(String slug, User user, String json) throws Exception {
		return api.send(put(base(slug) + "/squad-rules"), user, json);
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String base(String slug) {
		return "/api/leagues/" + slug;
	}

	private static String cardPath(String slug, String id) {
		return base(slug) + "/squad/cards/" + id;
	}

	private static String searchPath(String slug, String q) {
		return base(slug) + "/cards?q=" + q;
	}

}
