package com.popcornjournal.importer;

import com.popcornjournal.entity.MovieFormat;
import com.popcornjournal.importer.ImportReport.Message;
import com.popcornjournal.importer.SpreadsheetReader.SheetRow;
import com.popcornjournal.support.Titles;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.popcornjournal.importer.ColumnMapping.DESCRIPTION;
import static com.popcornjournal.importer.ColumnMapping.FORMAT;
import static com.popcornjournal.importer.ColumnMapping.GENRE;
import static com.popcornjournal.importer.ColumnMapping.LANGUAGE;
import static com.popcornjournal.importer.ColumnMapping.PLATFORM;
import static com.popcornjournal.importer.ColumnMapping.RATING;
import static com.popcornjournal.importer.ColumnMapping.RECOMMENDED_BY;
import static com.popcornjournal.importer.ColumnMapping.REVIEW;
import static com.popcornjournal.importer.ColumnMapping.THEATRE;
import static com.popcornjournal.importer.ColumnMapping.TITLE;
import static com.popcornjournal.importer.ColumnMapping.WATCH_DATE;
import static com.popcornjournal.importer.ColumnMapping.YEAR;

/**
 * Turns raw cells into typed values, one row at a time. Anything it can't interpret is
 * left empty with a warning; the original cell is still kept in the raw values.
 */
@Component
public class RowInterpreter {

    /** Spellings of the "beyond the scale" rating. */
    private static final Set<String> BEYOND_SCALE = Set.of("infinity", "inf", "∞");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH));

    private final Clock clock;

    public RowInterpreter(Clock clock) {
        this.clock = clock;
    }

    public record WatchedRow(int rowNumber, Map<String, Object> raw, String title, MovieFormat format,
                             Integer releaseYear, String language, String synopsis, Integer rating,
                             boolean ratingBeyondScale, String review, String genre, String platform,
                             String venue, LocalDate watchedOn, List<Message> messages) {

        public boolean hasErrors() {
            return messages.stream().anyMatch(m -> m.level() == ImportReport.Level.ERROR);
        }

        /** Title, format, year and language: the same identity the database enforces. */
        public String identityKey() {
            return Titles.key(title) + "|" + format + "|" + (releaseYear == null ? "" : releaseYear)
                    + "|" + (language == null ? "" : language.toLowerCase(Locale.ROOT));
        }
    }

    public record WatchlistRow(int rowNumber, Map<String, Object> raw, String title, String recommendedBy,
                               String genre, List<Message> messages) {

        public boolean hasErrors() {
            return messages.stream().anyMatch(m -> m.level() == ImportReport.Level.ERROR);
        }
    }

    public WatchedRow watched(SheetRow row, ColumnMapping.Resolved columns) {
        List<Message> messages = new ArrayList<>();
        Map<String, Object> values = row.values();

        String title = title(columns.value(TITLE, values), messages);
        MovieFormat format = format(columns.value(FORMAT, values), messages);
        Integer year = year(columns.value(YEAR, values), messages);
        String language = text(columns.value(LANGUAGE, values));
        if (language != null && Titles.key(language).equals("language")) {
            messages.add(Message.warning("Language", "The value 'Language' looks like a header; left empty"));
            language = null;
        } else if (language != null && language.length() > 40) {
            messages.add(Message.warning("Language", "Longer than 40 characters; left empty"));
            language = null;
        }

        Integer rating = null;
        boolean beyond = false;
        Object rawRating = columns.value(RATING, values);
        String ratingText = text(rawRating);
        if (ratingText != null) {
            if (BEYOND_SCALE.contains(ratingText.toLowerCase(Locale.ROOT))) {
                beyond = true;
            } else {
                rating = wholeNumber(rawRating);
                if (rating == null || rating < 1 || rating > 10) {
                    messages.add(Message.warning("Rating", "'" + ratingText + "' is not a 1-10 rating; left unrated"));
                    rating = null;
                }
            }
        }

        LocalDate watchedOn = date(columns.value(WATCH_DATE, values), messages);
        if (watchedOn != null && year != null && watchedOn.getYear() < year) {
            messages.add(Message.warning("Watch Date", "Watched before the release year; check the row"));
        }

        return new WatchedRow(row.rowNumber(), jsonFriendly(values), title, format, year, language,
                text(columns.value(DESCRIPTION, values)), rating, beyond,
                text(columns.value(REVIEW, values)), limited(columns.value(GENRE, values), 80, "Genre", messages),
                limited(columns.value(PLATFORM, values), 80, "Platform", messages),
                limited(columns.value(THEATRE, values), 160, "Theatre", messages), watchedOn, messages);
    }

    public WatchlistRow watchlist(SheetRow row, ColumnMapping.Resolved columns) {
        List<Message> messages = new ArrayList<>();
        Map<String, Object> values = row.values();
        String title = title(columns.value(TITLE, values), messages);
        return new WatchlistRow(row.rowNumber(), jsonFriendly(values), title,
                limited(columns.value(RECOMMENDED_BY, values), 160, "Recommended By", messages),
                limited(columns.value(GENRE, values), 80, "Genre", messages), messages);
    }

    /** Names longer than their column are dropped with a warning instead of failing the import. */
    private static String limited(Object value, int maxLength, String field, List<Message> messages) {
        String name = Titles.clean(text(value));
        if (name != null && name.length() > maxLength) {
            messages.add(Message.warning(field, "Longer than " + maxLength + " characters; left empty"));
            return null;
        }
        return name;
    }

    private static String title(Object value, List<Message> messages) {
        if (value instanceof Double number) {
            // a film called "2018" is stored by Excel as the number 2018
            String asText = formatNumber(number);
            messages.add(Message.info("Title", "Numeric title " + asText + " kept as text"));
            return asText;
        }
        String title = Titles.clean(text(value));
        if (title == null) {
            messages.add(Message.error("Title", "Row has no title"));
        } else if (title.length() > 250) {
            messages.add(Message.error("Title", "Title is longer than 250 characters"));
        }
        return title;
    }

    private static MovieFormat format(Object value, List<Message> messages) {
        String text = text(value);
        if (text == null) {
            messages.add(Message.info("Format", "No format given; recorded as Movie"));
            return MovieFormat.MOVIE;
        }
        return switch (Titles.key(text)) {
            case "movie", "film" -> MovieFormat.MOVIE;
            case "series", "show", "tvseries", "webseries" -> MovieFormat.SERIES;
            default -> {
                messages.add(Message.error("Format", "Unknown format '" + text + "'"));
                yield MovieFormat.MOVIE;
            }
        };
    }

    private static Integer year(Object value, List<Message> messages) {
        if (text(value) == null) {
            return null;
        }
        Integer year = wholeNumber(value);
        if (year == null || year < 1870 || year > 2100) {
            messages.add(Message.warning("Year", "'" + text(value) + "' is not a release year; left empty"));
            return null;
        }
        return year;
    }

    private LocalDate date(Object value, List<Message> messages) {
        LocalDate date = null;
        if (value instanceof LocalDateTime dateTime) {
            date = dateTime.toLocalDate();
        } else if (text(value) != null) {
            String text = text(value);
            for (DateTimeFormatter format : DATE_FORMATS) {
                try {
                    date = LocalDate.parse(text, format);
                    break;
                } catch (DateTimeParseException ignored) {
                    // try the next format
                }
            }
            if (date == null) {
                messages.add(Message.warning("Watch Date", "'" + text + "' is not a date; left undated"));
            }
        }
        if (date != null && date.isAfter(LocalDate.now(clock))) {
            messages.add(Message.warning("Watch Date", "Watch date " + date + " is in the future"));
        }
        return date;
    }

    /** A cell holding only "-" means "nothing recorded" in the source sheet. */
    static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value instanceof Double number ? formatNumber(number) : value.toString().strip();
        return text.isEmpty() || text.equals("-") ? null : text;
    }

    private static Integer wholeNumber(Object value) {
        try {
            BigDecimal number = value instanceof Double d ? BigDecimal.valueOf(d) : new BigDecimal(text(value));
            return number.stripTrailingZeros().scale() <= 0 ? number.intValueExact() : null;
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }

    private static String formatNumber(Double number) {
        return number == Math.rint(number) ? String.valueOf(number.longValue()) : number.toString();
    }

    /** Raw values ready for JSONB: dates become ISO strings, whole numbers lose their ".0". */
    private static Map<String, Object> jsonFriendly(Map<String, Object> values) {
        Map<String, Object> raw = new LinkedHashMap<>();
        values.forEach((header, value) -> raw.put(header, switch (value) {
            case LocalDateTime dateTime -> dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)
                    ? dateTime.toLocalDate().toString() : dateTime.toString();
            case Double number when number == Math.rint(number) && Math.abs(number) < 1e15 -> number.longValue();
            default -> value;
        }));
        return raw;
    }
}
