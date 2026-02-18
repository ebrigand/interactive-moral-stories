package com.istory.storyengine.repository;

import com.istory.storyengine.model.StorySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StorySessionRepository
        extends JpaRepository<StorySession, UUID> {
}
