package com.popcornjournal.importer;

import com.popcornjournal.importer.ImportReport.SpellingMerge;
import com.popcornjournal.support.Titles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the spellings of one kind of name (platform, venue, genre) seen in a sheet and
 * decides what each is stored as: the existing database name if there is one, otherwise
 * the most frequent spelling in the sheet.
 */
final class NameBook {

    private final String kind;
    private final Map<String, String> existingByKey = new HashMap<>();
    private final Map<String, Map<String, Integer>> spellingsByKey = new LinkedHashMap<>();

    NameBook(String kind, Collection<String> existingNames) {
        this.kind = kind;
        existingNames.forEach(name -> existingByKey.putIfAbsent(Titles.key(name), name));
    }

    /** Records a spelling and returns its key, or null for an empty name. */
    String see(String name) {
        String clean = Titles.clean(name);
        if (clean == null) {
            return null;
        }
        String key = Titles.key(clean);
        if (key.isEmpty()) {
            return null;
        }
        spellingsByKey.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(clean, 1, Integer::sum);
        return key;
    }

    /** The key of a name without recording it as a spelling. */
    static String keyOf(String name) {
        String clean = Titles.clean(name);
        String key = clean == null ? "" : Titles.key(clean);
        return key.isEmpty() ? null : key;
    }

    String storedName(String key) {
        String existing = existingByKey.get(key);
        if (existing != null) {
            return existing;
        }
        Map<String, Integer> spellings = spellingsByKey.get(key);
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : spellings.entrySet()) {
            int count = entry.getValue();
            // on a tie, the more capitalised spelling wins: "Jio Hotstar" over "jio Hotstar"
            if (count > bestCount || count == bestCount && capitals(entry.getKey()) > capitals(best)) {
                best = entry.getKey();
                bestCount = count;
            }
        }
        return best;
    }

    private static long capitals(String text) {
        return text == null ? 0 : text.chars().filter(Character::isUpperCase).count();
    }

    boolean exists(String key) {
        return existingByKey.containsKey(key);
    }

    /** Names that are not in the database yet, limited to the given keys. */
    List<String> newNames(Collection<String> usedKeys) {
        return usedKeys.stream().distinct().filter(key -> !exists(key)).map(this::storedName)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    List<SpellingMerge> merges() {
        List<SpellingMerge> merges = new ArrayList<>();
        spellingsByKey.forEach((key, spellings) -> {
            String stored = storedName(key);
            boolean differsFromStored = spellings.keySet().stream().anyMatch(s -> !s.equals(stored));
            if (differsFromStored) {
                merges.add(new SpellingMerge(kind, stored, List.copyOf(spellings.keySet())));
            }
        });
        return merges;
    }
}
