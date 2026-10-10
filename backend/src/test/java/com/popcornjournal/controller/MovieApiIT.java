package com.popcornjournal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcornjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MovieApiIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    static String movieJson(String title, Integer year, String language, Integer rating) {
        return """
                {"title": %s, "format": "MOVIE", "releaseYear": %s, "language": %s, "rating": %s,
                 "ratingBeyondScale": false, "favourite": false, "review": "Liked it", "genres": ["Drama - Journey"]}
                """.formatted(quote(title), year, quote(language), rating);
    }

    static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    long createMovie(String body) throws Exception {
        String response = mvc.perform(post("/api/v1/movies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    void createsAMovieWithAnInitialViewing() throws Exception {
        String body = """
                {"movie": %s,
                 "initialViewing": {"watchedOn": "2026-07-01", "platformName": "Netflix", "notes": "Late show"}}
                """.formatted(movieJson("Ronth", 2025, "Malayalam", 8));

        mvc.perform(post("/api/v1/movies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/movies/")))
                .andExpect(jsonPath("$.title").value("Ronth"))
                .andExpect(jsonPath("$.genres[0].name").value("Drama - Journey"))
                .andExpect(jsonPath("$.genres[0].category").value("Drama"))
                .andExpect(jsonPath("$.viewings", hasSize(1)))
                .andExpect(jsonPath("$.viewings[0].method").value("STREAMING"))
                .andExpect(jsonPath("$.viewings[0].platform.name").value("Netflix"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void aMovieCanBeAddedWithoutRatingOrReview() throws Exception {
        String body = """
                {"movie": {"title": "Later", "format": "MOVIE"}}""";

        mvc.perform(post("/api/v1/movies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(nullValue()))
                .andExpect(jsonPath("$.review").value(nullValue()))
                .andExpect(jsonPath("$.viewings", hasSize(0)));
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        String body = """
                {"movie": {"title": " ", "format": "MOVIE", "rating": 12, "releaseYear": 1500}}""";

        mvc.perform(post("/api/v1/movies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'movie.title')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'movie.rating')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'movie.releaseYear')]").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void bothRatingKindsAtOnceIsRejected() throws Exception {
        String body = """
                {"movie": {"title": "Both", "format": "MOVIE", "rating": 9, "ratingBeyondScale": true}}""";

        mvc.perform(post("/api/v1/movies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("not both")));
    }

    @Test
    void malformedJsonAndBadEnumsGiveA400() throws Exception {
        mvc.perform(post("/api/v1/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movie\": {\"title\": \"X\", \"format\": \"PODCAST\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("could not be read")));
    }

    @Test
    void exactDuplicatesAreRejectedButRemakesAreAllowed() throws Exception {
        createMovie("{\"movie\": " + movieJson("Drishyam", 2013, "Malayalam", 9) + "}");

        mvc.perform(post("/api/v1/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movie\": " + movieJson("drishyam", 2013, "malayalam", 7) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictsWith[0].title").value("Drishyam"));

        createMovie("{\"movie\": " + movieJson("Drishyam", 2015, "Hindi", 7) + "}");

        mvc.perform(get("/api/v1/movies/duplicates").param("title", "DRISHYAM").param("releaseYear", "2015")
                        .param("language", "Hindi").param("format", "MOVIE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.releaseYear == 2015)].exactMatch").value(true));
    }

    @Test
    void updatesPersistAndUpdatedAtMoves() throws Exception {
        long id = createMovie("{\"movie\": " + movieJson("Youth", 2026, "Tamil", 4) + "}");
        String before = mvc.perform(get("/api/v1/movies/" + id)).andReturn().getResponse().getContentAsString();

        mvc.perform(put("/api/v1/movies/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Youth", "format": "MOVIE", "releaseYear": 2026, "language": "Tamil",
                                 "ratingBeyondScale": true, "review": "Changed my mind",
                                 "privateNotes": "watch with friends", "genres": ["Comedy - Rom", "Feel Good"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(nullValue()))
                .andExpect(jsonPath("$.ratingBeyondScale").value(true))
                .andExpect(jsonPath("$.genres", hasSize(2)));

        JsonNode after = json.readTree(mvc.perform(get("/api/v1/movies/" + id)).andReturn().getResponse()
                .getContentAsString());
        assertThat(after.get("review").asText()).isEqualTo("Changed my mind");
        assertThat(after.get("privateNotes").asText()).isEqualTo("watch with friends");
        assertThat(after.get("updatedAt").asText()).isNotEqualTo(json.readTree(before).get("updatedAt").asText());
    }

    @Test
    void favouriteToggle() throws Exception {
        long id = createMovie("{\"movie\": " + movieJson("Sinners", 2025, "English", 8) + "}");

        mvc.perform(patch("/api/v1/movies/" + id + "/favourite").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favourite\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favourite").value(true));

        mvc.perform(get("/api/v1/movies").param("list", "FAVOURITES"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deletingAMovieRemovesItsViewings() throws Exception {
        long id = createMovie("""
                {"movie": %s, "initialViewing": {"watchedOn": "2026-07-01", "venueName": "PVR"}}
                """.formatted(movieJson("Karuppu", 2026, "Tamil", 7)));

        mvc.perform(delete("/api/v1/movies/" + id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/movies/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Movie " + id + " was not found"));
        assertThat(count("viewing_sessions")).isZero();
        assertThat(count("venues")).isEqualTo(1);
    }

    @Test
    void unknownIdsAndRoutesAre404s() throws Exception {
        mvc.perform(get("/api/v1/movies/999")).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/movies/999").contentType(MediaType.APPLICATION_JSON)
                .content(movieJson("Nope", null, null, null))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/movies/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/nothing-here")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/movies/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void healthReportsTheDatabase() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.schemaVersion").exists());
    }
}
