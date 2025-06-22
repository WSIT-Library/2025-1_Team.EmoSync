package com.example.capstone.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class EmitterRepository {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter findById(String userId) {
        return emitters.get(userId);
    }

    public SseEmitter save(String userId, SseEmitter sseEmitter) {
        emitters.put(userId, sseEmitter);
        return emitters.get(userId);
    }

    public boolean existById(String userId) {
        return emitters.containsKey(userId);
    }

    public void deleteById(String userId) {
        emitters.remove(userId);
    }
}
