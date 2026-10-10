package com.popcornjournal.entity;

import com.popcornjournal.support.Titles;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "movies")
public class Movie extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(name = "original_title", length = 250)
    private String originalTitle;

    @Column(name = "title_key", nullable = false, length = 250)
    private String titleKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MovieFormat format = MovieFormat.MOVIE;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(length = 40)
    private String language;

    @Column(columnDefinition = "text")
    private String synopsis;

    private Integer rating;

    @Column(name = "rating_beyond_scale", nullable = false)
    private boolean ratingBeyondScale;

    @Column(columnDefinition = "text")
    private String review;

    @Column(name = "private_notes", columnDefinition = "text")
    private String privateNotes;

    @Column(nullable = false)
    private boolean favourite;

    @Column(length = 250)
    private String director;

    @Column(name = "cast_members", columnDefinition = "text")
    private String castMembers;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "metadata_source", length = 120)
    private String metadataSource;

    @Column(name = "import_key", length = 400, updatable = false)
    private String importKey;

    @ManyToMany
    @JoinTable(name = "movie_genres",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    private Set<Genre> genres = new LinkedHashSet<>();

    /** Sets the rating tier; a beyond-scale rating has no numeric value. */
    public void rate(Integer rating, boolean beyondScale) {
        this.ratingBeyondScale = beyondScale;
        this.rating = beyondScale ? null : rating;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titleKey = Titles.key(title);
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public MovieFormat getFormat() {
        return format;
    }

    public void setFormat(MovieFormat format) {
        this.format = format;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    public Integer getRating() {
        return rating;
    }

    public boolean isRatingBeyondScale() {
        return ratingBeyondScale;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public String getPrivateNotes() {
        return privateNotes;
    }

    public void setPrivateNotes(String privateNotes) {
        this.privateNotes = privateNotes;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getCastMembers() {
        return castMembers;
    }

    public void setCastMembers(String castMembers) {
        this.castMembers = castMembers;
    }

    public Integer getRuntimeMinutes() {
        return runtimeMinutes;
    }

    public void setRuntimeMinutes(Integer runtimeMinutes) {
        this.runtimeMinutes = runtimeMinutes;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public String getMetadataSource() {
        return metadataSource;
    }

    public void setMetadataSource(String metadataSource) {
        this.metadataSource = metadataSource;
    }

    public String getImportKey() {
        return importKey;
    }

    public void setImportKey(String importKey) {
        this.importKey = importKey;
    }

    public Set<Genre> getGenres() {
        return genres;
    }

    public void replaceGenres(Set<Genre> newGenres) {
        genres.clear();
        genres.addAll(newGenres);
    }
}
