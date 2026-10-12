package com.popcornjournal.poster;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds posters through the English Wikipedia API: one search request returns candidate
 * articles with their lead image. Film articles use the theatrical poster as that image.
 * Requests carry an identifying User-Agent as Wikimedia's API policy asks.
 */
@Component
public class WikipediaPosterProvider implements PosterProvider {

    private static final long MAX_IMAGE_BYTES = 3 * 1024 * 1024;

    private final RestClient http;
    private final PosterProperties properties;

    public WikipediaPosterProvider(RestClient.Builder builder, PosterProperties properties) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(5_000);
        timeouts.setReadTimeout(15_000);
        this.http = builder.requestFactory(timeouts)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
        this.properties = properties;
    }

    @Override
    public String name() {
        return "Wikipedia";
    }

    @Override
    public List<PosterMatcher.Candidate> search(String searchText) {
        URI uri = UriComponentsBuilder.fromUriString(properties.wikipediaApi())
                .queryParam("action", "query")
                .queryParam("format", "json")
                .queryParam("formatversion", "2")
                .queryParam("generator", "search")
                .queryParam("gsrsearch", "{q}")
                .queryParam("gsrlimit", "6")
                .queryParam("gsrnamespace", "0")
                .queryParam("prop", "pageimages|description")
                .queryParam("piprop", "thumbnail")
                .queryParam("pithumbsize", String.valueOf(properties.thumbnailWidth()))
                // film posters are non-free files; "any" includes them
                .queryParam("pilicense", "any")
                .queryParam("maxlag", "5")
                // encode before expanding so "&" in "Harry Potter & ..." can't split the query
                .encode()
                .buildAndExpand(searchText)
                .toUri();

        JsonNode body = http.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
        List<PosterMatcher.Candidate> candidates = new ArrayList<>();
        if (body == null || !body.path("query").path("pages").isArray()) {
            return candidates;
        }
        for (JsonNode page : body.path("query").path("pages")) {
            String title = page.path("title").asText();
            String image = page.path("thumbnail").path("source").asText(null);
            candidates.add(new PosterMatcher.Candidate(
                    title,
                    page.path("description").asText(""),
                    image == null ? null : stripQuery(image),
                    "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8),
                    page.path("index").asInt(99)));
        }
        return candidates;
    }

    @Override
    public Optional<Image> download(String imageUrl) {
        URI uri = URI.create(imageUrl);
        // only Wikimedia's own image hosts, never an arbitrary URL from a response
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || !host.endsWith(".wikimedia.org")) {
            return Optional.empty();
        }
        ResponseEntity<byte[]> response = http.get().uri(uri).retrieve().toEntity(byte[].class);
        byte[] bytes = response.getBody();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            return Optional.empty();
        }
        // the type comes from the bytes themselves, not the Content-Type header
        return ImageTypes.detect(bytes).map(contentType -> new Image(bytes, contentType));
    }

    /** Wikimedia appends tracking parameters (utm_source=…) that aren't needed. */
    private static String stripQuery(String url) {
        int question = url.indexOf('?');
        return question < 0 ? url : url.substring(0, question);
    }
}
