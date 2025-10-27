package com.Color_craze.controllers;

import org.bson.Document;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de diagnóstico para verificar conectividad con MongoDB Atlas en producción.
 */
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private final MongoTemplate mongoTemplate;
    private final Environment env;

    public DiagnosticsController(MongoTemplate mongoTemplate, Environment env) {
        this.mongoTemplate = mongoTemplate;
        this.env = env;
    }

    @GetMapping("/mongo")
    public ResponseEntity<Map<String, Object>> mongo() {
        Map<String, Object> out = new LinkedHashMap<>();
        String uri = env.getProperty("spring.data.mongodb.uri", "N/A");
        out.put("configuredUriHost", sanitizeUri(uri));
        try {
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            out.put("ok", true);
            out.put("commandResult", result == null ? "null" : result.toJson());
            return ResponseEntity.ok(out);
        } catch (Exception ex) {
            out.put("ok", false);
            out.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return ResponseEntity.status(503).body(out);
        }
    }

    private String sanitizeUri(String raw) {
        try {
            if (raw == null || raw.isBlank()) return "N/A";
            // Oculta credenciales, deja host y base
            // mongodb+srv://user:pass@host/db?params -> mongodb+srv://***:***@host/db
            int at = raw.indexOf('@');
            int scheme = raw.indexOf("://");
            if (scheme > -1 && at > scheme) {
                String prefix = raw.substring(0, scheme + 3);
                String rest = raw.substring(at); // incluye '@'
                // Quita query params para que no se impriman
                URI parsed = URI.create("http://" + rest.substring(1)); // hack para parsear host/path
                String host = parsed.getHost();
                String path = parsed.getPath();
                if (path == null) path = "";
                return prefix + "***:***@" + host + path;
            }
            return raw;
        } catch (Exception e) {
            return "hidden";
        }
    }
}
