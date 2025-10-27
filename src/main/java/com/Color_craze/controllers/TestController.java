package com.Color_craze.controllers;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Color-Craze API");
        response.put("timestamp", System.currentTimeMillis());
        response.put("message", "Service is running without database");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/echo")
    public ResponseEntity<?> echo(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("received", data);
        response.put("timestamp", System.currentTimeMillis());
        response.put("message", "Echo successful");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Map<String, Object> response = new HashMap<>();
        response.put("backend", "online");
        response.put("database", "disabled");
        response.put("auth", "disabled");
        response.put("cors", "enabled");
        return ResponseEntity.ok(response);
    }

    // Simular endpoints de autenticación para testing
    @PostMapping("/mock-register")
    public ResponseEntity<?> mockRegister(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Mock registration successful");
        response.put("user", data.get("username"));
        response.put("token", "mock-jwt-token-" + System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mock-login")
    public ResponseEntity<?> mockLogin(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Mock login successful");
        response.put("user", data.get("username"));
        response.put("token", "mock-jwt-token-" + System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}