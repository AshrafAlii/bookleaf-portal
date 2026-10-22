package com.bookleaf.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

/**
 * Serves the single-page application.
 *
 * <p>Client-side routes are <em>forwarded</em> (not redirected) to
 * {@code index.html} so a browser refresh keeps the URL in the address bar and
 * React Router can pick it up. The mapping is deliberately limited to the
 * application's own routes so that static assets under {@code /assets} are
 * served by the normal resource handler.</p>
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/author", "/author/**", "/admin", "/admin/**"})
    public String forwardToSpa() {
        return "forward:/index.html";
    }

    /**
     * Unknown API paths return a JSON 404 rather than the HTML shell, so API
     * clients always get a parseable response.
     */
    @RestController
    @RequestMapping("/api")
    public static class ApiNotFoundController {

        @GetMapping("/**")
        public ResponseEntity<java.util.Map<String, Object>> notFound() {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("status", 404);
            body.put("error", "Not Found");
            body.put("message", "The requested API endpoint does not exist. "
                    + "See /swagger-ui.html for the full API reference.");
            return ResponseEntity.status(404).body(body);
        }
    }
}
