package com.semih.randevuapi.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds URL-friendly slugs from titles: "Spring Boot ile JWT Kimlik Doğrulama"
 * becomes "spring-boot-ile-jwt-kimlik-dogrulama".
 */
public final class Slugs {

    /** Lowercase letters and digits, single hyphens between words. */
    public static final String PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    public static final int MAX_LENGTH = 120;

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    private Slugs() {
        // utility class
    }

    /** @return the slug, or an empty string if the text has no letters or digits */
    public static String from(String text) {
        // Turkish lowercase first, so "I" becomes "ı" and "İ" becomes "i", then map to ASCII
        String lower = text.toLowerCase(TURKISH)
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ı', 'i')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u');
        // Strip any remaining accents (é → e)
        String ascii = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug;
    }
}
