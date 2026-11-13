package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.storage.StorageException;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A single parsed CSV data row whose values are accessed by column name.
 * Parsing failures are reported with the file name, line number, column, and offending token.
 */
final class CsvRecord {
    private final String fileName;
    private final List<String> columns;
    private final List<String> values;
    private final int lineNumber;

    CsvRecord(String fileName, List<String> columns, List<String> values, int lineNumber) {
        this.fileName = Objects.requireNonNull(fileName, "File name must not be null");
        this.columns = List.copyOf(columns);
        this.values = List.copyOf(values);
        this.lineNumber = lineNumber;
        if (this.values.stream().allMatch(String::isBlank)) {
            throw new StorageException("Empty record in " + fileName + " at line " + lineNumber
                    + ": the row contains only delimiters and no values; remove the line or fill in its fields");
        }
        if (this.values.size() < this.columns.size()) {
            throw new StorageException("Malformed row in " + fileName + " at line " + lineNumber);
        }
    }

    String text(String column) {
        return values.get(indexOf(column));
    }

    <T> T required(String column, Function<String, T> parser) {
        String raw = text(column);
        try {
            return parser.apply(raw.strip());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new StorageException("Malformed value in " + fileName + " at line " + lineNumber
                    + ", column '" + column + "': invalid token '" + raw + "'", e);
        }
    }

    <T> T optional(String column, Function<String, T> parser) {
        return text(column).isBlank() ? null : required(column, parser);
    }

    private int indexOf(String column) {
        int index = columns.indexOf(column);
        if (index < 0) {
            throw new IllegalStateException("Unknown column '" + column + "' in " + fileName);
        }
        return index;
    }
}
