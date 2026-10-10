package com.popcornjournal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcornjournal.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ViewingSessionApiIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    long movieId;

    @BeforeEach
    void movie() throws Exception {
        String response = mvc.perform(post("/api/v1/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movie\": {\"title\": \"Project Hail Mary\", \"format\": \"MOVIE\", \"rating\": 10}}"))
                .andReturn().getResponse().getContentAsString();
        movieId = json.readTree(response).get("id").asLong();
    }

    long addViewing(String body) throws Exception {
        String response = mvc.perform(post("/api/v1/movies/" + movieId + "/viewings")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    void methodAndPlatformAreSeparate() throws Exception {
        addViewing("""
                {"watchedOn": "2026-04-12", "watchedAtTime": "21:30", "method": "CINEMA",
                 "venueName": "PVR Nexus", "location": "Bengaluru", "rating": 9, "notes": "IMAX"}""");
        addViewing("""
                {"method": "STREAMING", "platformName": "Prime Video"}""");

        mvc.perform(get("/api/v1/movies/" + movieId + "/viewings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].watchedOn").value("2026-04-12"))
                .andExpect(jsonPath("$[0].watchedAtTime").value("21:30:00"))
                .andExpect(jsonPath("$[0].venue.name").value("PVR Nexus"))
                .andExpect(jsonPath("$[0].rating").value(9))
                .andExpect(jsonPath("$[1].watchedOn").doesNotExist())
                .andExpect(jsonPath("$[1].platform.name").value("Prime Video"));

        // movie-level rating is untouched by the viewing's rating
        mvc.perform(get("/api/v1/movies/" + movieId)).andExpect(jsonPath("$.rating").value(10));
    }

    @Test
    void platformNamesResolveToExistingSpellings() throws Exception {
        addViewing("{\"platformName\": \"Zee 5\"}");
        addViewing("{\"platformName\": \"zee5\"}");

        assertThat(count("platforms")).isEqualTo(1);
        mvc.perform(get("/api/v1/platforms"))
                .andExpect(jsonPath("$[0].name").value("Zee 5"))
                .andExpect(jsonPath("$[0].viewingCount").value(2));
    }

    @Test
    void editingAViewingPersists() throws Exception {
        long id = addViewing("{\"watchedOn\": \"2026-04-12\", \"platformName\": \"Netflix\"}");

        mvc.perform(put("/api/v1/viewings/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchedOn\": \"2026-04-13\", \"method\": \"TELEVISION\", \"notes\": \"On TV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("TELEVISION"))
                .andExpect(jsonPath("$.platform").doesNotExist());

        mvc.perform(get("/api/v1/viewings/" + id))
                .andExpect(jsonPath("$.watchedOn").value("2026-04-13"))
                .andExpect(jsonPath("$.notes").value("On TV"));
    }

    @Test
    void deletingAViewingKeepsTheMovie() throws Exception {
        long first = addViewing("{\"watchedOn\": \"2026-04-12\"}");
        addViewing("{\"watchedOn\": \"2026-05-01\"}");

        mvc.perform(delete("/api/v1/viewings/" + first)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/movies/" + movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewings", hasSize(1)));
        mvc.perform(delete("/api/v1/viewings/" + first)).andExpect(status().isNotFound());
    }

    @Test
    void invalidViewingsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/movies/" + movieId + "/viewings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchedAtTime\": \"20:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("needs a watch date")));

        mvc.perform(post("/api/v1/movies/" + movieId + "/viewings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchedOn\": \"2099-01-01\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/movies/424242/viewings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchedOn\": \"2026-01-01\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void diaryListsNewestFirstWithUndatedLast() throws Exception {
        addViewing("{\"platformName\": \"Netflix\"}");
        addViewing("{\"watchedOn\": \"2025-12-31\"}");
        addViewing("{\"watchedOn\": \"2026-02-01\"}");

        mvc.perform(get("/api/v1/viewings"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].watchedOn").value("2026-02-01"))
                .andExpect(jsonPath("$.content[2].watchedOn").doesNotExist());
        mvc.perform(get("/api/v1/viewings").param("year", "2025"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/viewings").param("undated", "true"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void platformsInUseCannotBeDeleted() throws Exception {
        addViewing("{\"platformName\": \"Netflix\"}");
        Long platformId = jdbc.queryForObject("SELECT id FROM platforms", Long.class);

        mvc.perform(delete("/api/v1/platforms/" + platformId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("used by 1 viewing")));
    }
}
