package com.popcornjournal.service;

import com.popcornjournal.IntegrationTest;
import com.popcornjournal.poster.PosterProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With the default configuration nothing is ever sent to the poster source. */
class PosterDisabledIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PosterProvider provider;

    @Test
    void lookupIsRefusedAndTheSourceIsNeverCalled() throws Exception {
        jdbc.update("INSERT INTO movies (title, title_key, format) VALUES ('Ronth', 'ronth', 'MOVIE')");

        mvc.perform(get("/api/v1/posters/status")).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/posters/lookup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("POSTERS_ENABLED")));
        verify(provider, never()).search(any());
    }

    @Test
    void uploadingWorksWithLookupTurnedOff() throws Exception {
        Long id = jdbc.queryForObject(
                "INSERT INTO movies (title, title_key, format) VALUES ('Rythmn', 'rythmn', 'MOVIE') RETURNING id", Long.class);
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        mvc.perform(multipart("/api/v1/movies/" + id + "/poster")
                        .file(new MockMultipartFile("file", "rythmn.webp", "image/webp", webp)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/movies/" + id + "/poster"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"));
    }
}
