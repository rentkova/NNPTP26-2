package cz.upce.fei.nnptp.taskpulse.domain;

import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;

import java.util.Locale;

public final class Label {
    /**
     * Character reserved for joining labels into a single serialized list; labels must not contain it.
     */
    public static final String SEPARATOR = ";";

    private Label() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Label must not be blank");
        }
        if (raw.contains(SEPARATOR)) {
            throw new ValidationException("Label must not contain '" + SEPARATOR
                    + "' because it separates labels in storage: '" + raw + "'");
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }
}
