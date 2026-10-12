package com.popcornjournal.poster;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Identifies an image by its first bytes, so a file's name or declared type is never trusted. */
public final class ImageTypes {

    private ImageTypes() {
    }

    public static Optional<String> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if ((bytes[0] & 0xFF) == 0x89 && ascii(bytes, 1, 3).equals("PNG")) {
            return Optional.of("image/png");
        }
        if (ascii(bytes, 0, 4).equals("GIF8")) {
            return Optional.of("image/gif");
        }
        if (ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP")) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
