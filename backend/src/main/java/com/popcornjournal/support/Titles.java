package com.popcornjournal.support;

import java.text.Normalizer;
import java.util.Locale;

/** Title normalisation shared by duplicate detection, search and the importer. */
public final class Titles {

    private Titles() {
    }

    /** Trim and collapse internal whitespace, keeping the title as typed otherwise. */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = raw.strip().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    /**
     * Lower-cased letters and digits only, so "F.R.I.E.N.D.S" and "Friends" share a key.
     * Accents are folded; non-Latin scripts are kept as they are.
     */
    public static String key(String title) {
        if (title == null) {
            return "";
        }
        String folded = Normalizer.normalize(title, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    /** Category of a compound genre label: "Thriller - Crime" -> "Thriller". */
    public static String genreCategory(String genreName) {
        String name = clean(genreName);
        if (name == null) {
            return null;
        }
        String[] parts = name.split("\\s+[-/]\\s+", 2);
        return parts[0].strip();
    }
}
