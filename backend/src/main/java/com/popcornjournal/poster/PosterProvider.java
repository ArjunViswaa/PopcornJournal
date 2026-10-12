package com.popcornjournal.poster;

import java.util.List;
import java.util.Optional;

/** A source of poster images. Implementations must not be called when the feature is off. */
public interface PosterProvider {

    String name();

    /** Search results for a query text; matching is done by {@link PosterMatcher}. */
    List<PosterMatcher.Candidate> search(String searchText);

    /** Downloads an image, or empty if it isn't an acceptable image. */
    Optional<Image> download(String imageUrl);

    record Image(byte[] bytes, String contentType) {
    }
}
