package com.example.demo.application.ai;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class IncidentTextSanitizer {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern DNI = Pattern.compile("(?i)\\b(?:DNI\\s*)?\\d{7,8}\\b");
    private static final Pattern PHONE = Pattern.compile("(?i)\\b(?:\\+?54\\s*)?(?:11[-\\s]?)?\\d{4}[-\\s]?\\d{4}\\b");
    private static final Pattern HTML = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern PERSON_AFTER_ADDRESS = Pattern.compile("(?iu)(domicilio\\s+de)\\s+[\\p{L}]+(?:\\s+[\\p{L}]+){0,3}");
    private static final Pattern INJECTION = Pattern.compile(
            "(?iu)(ignor[aáe].{0,40}(reglas|instrucciones)|respond[eé].{0,20}fuera.{0,20}json|"
                    + "ejecut[aáe].{0,40}(drop|delete|update|insert)|confirm[aáe].{0,30}(hizo|ejecut)|"
                    + "soy\\s+el\\s+intendente|aprob[aáe].{0,30}obra|asign[aáe].{0,30}cuadrilla)"
    );

    public SanitizedIncident sanitize(String raw) {
        String value = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFKC);
        int redactions = 0;
        String next = replace(value, HTML, " "); redactions += changed(value, next); value = next;
        next = replace(value, EMAIL, "[DATO_PERSONAL]"); redactions += changed(value, next); value = next;
        next = replace(value, PHONE, "[DATO_PERSONAL]"); redactions += changed(value, next); value = next;
        next = replace(value, DNI, "[DATO_PERSONAL]"); redactions += changed(value, next); value = next;
        next = PERSON_AFTER_ADDRESS.matcher(value).replaceAll("$1 [DATO_PERSONAL]"); redactions += changed(value, next); value = next;
        next = replace(value, INJECTION, " "); redactions += changed(value, next); value = next;
        value = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return new SanitizedIncident(value, redactions > 0);
    }

    public String normalized(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String replace(String value, Pattern pattern, String replacement) {
        return pattern.matcher(value).replaceAll(replacement);
    }

    private static int changed(String before, String after) {
        return before.equals(after) ? 0 : 1;
    }

    public record SanitizedIncident(String text, boolean redacted) { }
}
