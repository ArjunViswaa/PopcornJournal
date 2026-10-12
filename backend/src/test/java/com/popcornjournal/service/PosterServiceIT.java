package com.popcornjournal.service;

import com.popcornjournal.IntegrationTest;
import com.popcornjournal.poster.PosterMatcher.Candidate;
import com.popcornjournal.poster.PosterProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The provider is mocked: these tests never call Wikipedia. */
@TestPropertySource(properties = "popcorn.posters.enabled=true")
class PosterServiceIT extends IntegrationTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PosterProvider provider;

    long ronth;
    long unknown;
    long ownPoster;

    @BeforeEach
    void seed() {
        ronth = insert("Ronth", 2025, "Malayalam", null);
        unknown = insert("Some Unknown Film", 2024, "Tamil", null);
        ownPoster = insert("Sinners", 2025, "English", "https://example.org/mine.jpg");

        when(provider.name()).thenReturn("Wikipedia");
        when(provider.search(any())).thenAnswer(call -> {
            String searchText = call.getArgument(0);
            return searchText.startsWith("Ronth 2025")
                    ? List.of(new Candidate("Ronth", "2025 Indian Malayalam film",
                    "https://upload.wikimedia.org/ronth.jpg", "https://en.wikipedia.org/wiki/Ronth", 1))
                    : List.of();
        });
        when(provider.download(anyString())).thenReturn(Optional.of(new PosterProvider.Image(JPEG, "image/jpeg")));
    }

    long insert(String title, int year, String language, String posterUrl) {
        Long id = jdbc.queryForObject("""
                        INSERT INTO movies (title, title_key, format, release_year, language, poster_url)
                        VALUES (?, ?, 'MOVIE', ?, ?, ?) RETURNING id""", Long.class,
                title, com.popcornjournal.support.Titles.key(title), year, language, posterUrl);
        return id == null ? 0 : id;
    }

    @Test
    void bulkLookupStoresMatchesAndRecordsMisses() throws Exception {
        mvc.perform(post("/api/v1/posters/lookup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.results[?(@.title == 'Ronth')].outcome").value("FOUND"))
                .andExpect(jsonPath("$.results[?(@.title == 'Some Unknown Film')].outcome").value("NOT_FOUND"));

        // the user's own poster was never looked up
        verify(provider, never()).search(org.mockito.ArgumentMatchers.argThat(text -> text.contains("Sinners")));

        mvc.perform(get("/api/v1/posters/status"))
                .andExpect(jsonPath("$.withFetchedPoster").value(1))
                .andExpect(jsonPath("$.withOwnPoster").value(1))
                .andExpect(jsonPath("$.notFound").value(1))
                .andExpect(jsonPath("$.pending").value(0));

        // a second run has nothing to do
        mvc.perform(post("/api/v1/posters/lookup")).andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void storedPosterIsServedAndShownInListsAndDetail() throws Exception {
        mvc.perform(post("/api/v1/posters/lookup")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/movies/" + ronth + "/poster"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes(JPEG));
        mvc.perform(get("/api/v1/movies").param("q", "ronth"))
                .andExpect(jsonPath("$.content[0].posterUrl", startsWith("/api/v1/movies/" + ronth + "/poster?v=")));
        mvc.perform(get("/api/v1/movies/" + ronth))
                .andExpect(jsonPath("$.posterUrl").doesNotExist())
                .andExpect(jsonPath("$.fetchedPoster.source").value("Wikipedia"))
                .andExpect(jsonPath("$.fetchedPoster.matchedTitle").value("Ronth"));
        // a user URL wins over nothing being fetched for it
        mvc.perform(get("/api/v1/movies").param("q", "sinners"))
                .andExpect(jsonPath("$.content[0].posterUrl").value("https://example.org/mine.jpg"));
    }

    @Test
    void removedPostersAreNotFetchedAgainByBulkRuns() throws Exception {
        mvc.perform(post("/api/v1/posters/lookup")).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/movies/" + ronth + "/poster")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/movies/" + ronth + "/poster")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/posters/lookup").param("retryNotFound", "true"))
                .andExpect(jsonPath("$.results[?(@.title == 'Ronth')]").isEmpty());

        // an explicit single lookup still works
        mvc.perform(post("/api/v1/movies/" + ronth + "/poster/lookup")).andExpect(jsonPath("$.outcome").value("FOUND"));
    }

    @Test
    void movieEditsDontChangeAndArentChangedByPosterBookkeeping() throws Exception {
        String before = jdbc.queryForObject("SELECT updated_at::text FROM movies WHERE id = ?", String.class, ronth);
        mvc.perform(post("/api/v1/posters/lookup")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT updated_at::text FROM movies WHERE id = ?", String.class, ronth))
                .isEqualTo(before);
    }

    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    @Test
    void uploadedPostersAreServedWithTheirRealTypeAndNeverReplacedByLookups() throws Exception {
        mvc.perform(multipart("/api/v1/movies/" + unknown + "/poster").file(file("C:\\fakepath\\mine.png", PNG)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/movies/" + unknown + "/poster"))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));
        mvc.perform(get("/api/v1/movies/" + unknown))
                .andExpect(jsonPath("$.fetchedPoster.source").value("Upload"))
                .andExpect(jsonPath("$.fetchedPoster.matchedTitle").value("mine.png"));

        // bulk runs skip it, and a single lookup is refused rather than overwriting it
        mvc.perform(post("/api/v1/posters/lookup").param("retryNotFound", "true"))
                .andExpect(jsonPath("$.results[?(@.title == 'Some Unknown Film')]").isEmpty());
        mvc.perform(post("/api/v1/movies/" + unknown + "/poster/lookup")).andExpect(status().isConflict());

        // uploading again replaces the image
        mvc.perform(multipart("/api/v1/movies/" + unknown + "/poster").file(file("other.jpg", JPEG_FULL)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/movies/" + unknown + "/poster")).andExpect(header().string("Content-Type", "image/jpeg"));
    }

    static final byte[] JPEG_FULL = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};

    @Test
    void uploadsThatAreNotImagesAreRejected() throws Exception {
        mvc.perform(multipart("/api/v1/movies/" + unknown + "/poster")
                        .file(file("poster.png", "<script>alert(1)</script>".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Upload a JPEG, PNG, WebP or GIF image"));
        mvc.perform(multipart("/api/v1/movies/999999/poster").file(file("x.png", PNG)))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/movies/" + ownPoster + "/poster").file(file("x.png", PNG)))
                .andExpect(status().isConflict());
        assertThat(count("movie_posters")).isZero();
    }

    @Test
    void singleLookupRefusesAMovieWithItsOwnPosterUrl() throws Exception {
        mvc.perform(post("/api/v1/movies/" + ownPoster + "/poster/lookup"))
                .andExpect(status().isConflict());
    }

    @Test
    void networkFailuresAreRecordedAndRetriedByTheNextRun() throws Exception {
        doThrow(new IllegalStateException("timeout")).when(provider).search(any());

        mvc.perform(post("/api/v1/posters/lookup"))
                .andExpect(jsonPath("$.results[0].outcome").value("FAILED"))
                .andExpect(jsonPath("$.remaining").value(0));
        mvc.perform(get("/api/v1/posters/status")).andExpect(jsonPath("$.pending").value(2));
    }

    @Test
    void deletingAMovieDeletesItsPoster() throws Exception {
        mvc.perform(post("/api/v1/posters/lookup")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/movies/" + ronth)).andExpect(status().isNoContent());
        assertThat(count("movie_posters")).isZero();
    }
}
