package com.popcornjournal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "viewing_sessions")
public class ViewingSession extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false, updatable = false)
    private Movie movie;

    @Column(name = "watched_on")
    private LocalDate watchedOn;

    @Column(name = "watched_at_time")
    private LocalTime watchedAtTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ViewingMethod method;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id")
    private Platform platform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(length = 200)
    private String location;

    private Integer rating;

    @Column(name = "rating_beyond_scale", nullable = false)
    private boolean ratingBeyondScale;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "import_key", length = 420, updatable = false)
    private String importKey;

    protected ViewingSession() {
    }

    public ViewingSession(Movie movie) {
        this.movie = movie;
    }

    public void rate(Integer rating, boolean beyondScale) {
        this.ratingBeyondScale = beyondScale;
        this.rating = beyondScale ? null : rating;
    }

    public Long getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public LocalDate getWatchedOn() {
        return watchedOn;
    }

    public void setWatchedOn(LocalDate watchedOn) {
        this.watchedOn = watchedOn;
    }

    public LocalTime getWatchedAtTime() {
        return watchedAtTime;
    }

    public void setWatchedAtTime(LocalTime watchedAtTime) {
        this.watchedAtTime = watchedAtTime;
    }

    public ViewingMethod getMethod() {
        return method;
    }

    public void setMethod(ViewingMethod method) {
        this.method = method;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getRating() {
        return rating;
    }

    public boolean isRatingBeyondScale() {
        return ratingBeyondScale;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getImportKey() {
        return importKey;
    }

    public void setImportKey(String importKey) {
        this.importKey = importKey;
    }
}
