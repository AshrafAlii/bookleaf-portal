package com.bookleaf.portal.web;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Serves the single-page application.
 *
 * <p>Two jobs:</p>
 * <ol>
 *   <li>Forward client-side routes (e.g. {@code /author/tickets/abc}) to
 *       {@code index.html} so a page refresh does not 404. API paths are excluded
 *       so genuine API 404s stay JSON.</li>
 *   <li>Return a small JSON body for unknown {@code /api} paths instead of the
 *       default HTML error page.</li>
 * </ol>
 */
@Controller
public class SpaController implements ErrorController {

    @GetMapping(value = {"/{path:[^\\.]*}", "/{path:[^\\.]*}/**"})
    public RedirectView forwardToSpa() {
        return new RedirectView("/index.html", true);
    }

    @RestController
    @RequestMapping("/api")
    public static class ApiNotFoundController {

        @GetMapping("/**")
        public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> notFound() {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("status", 404);
            body.put("error", "Not Found");
            body.put("message", "The requested API endpoint does not exist. "
                    + "See /swagger-ui.html for the full API reference.");
            return org.springframework.http.ResponseEntity.status(404).body(body);
        }
    }
}
