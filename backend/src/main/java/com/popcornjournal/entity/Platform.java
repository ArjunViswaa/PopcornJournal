package com.popcornjournal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platforms")
public class Platform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_method", nullable = false, length = 20)
    private ViewingMethod defaultMethod = ViewingMethod.STREAMING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Platform() {
    }

    public Platform(String name, ViewingMethod defaultMethod) {
        this.name = name;
        this.defaultMethod = defaultMethod;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ViewingMethod getDefaultMethod() {
        return defaultMethod;
    }

    public void setDefaultMethod(ViewingMethod defaultMethod) {
        this.defaultMethod = defaultMethod;
    }
}
