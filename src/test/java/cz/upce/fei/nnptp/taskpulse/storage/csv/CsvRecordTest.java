package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.storage.StorageException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvRecordTest {

    private static final List<String> COLUMNS = List.of("name", "count");

    @Test
    void rejectsDelimiterOnlyRow() {
        assertThatThrownBy(() -> new CsvRecord("items.csv", COLUMNS, List.of("", " "), 3))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Empty record in items.csv at line 3")
                .hasMessageContaining("only delimiters");
    }

    @Test
    void rejectsRowWithMissingColumns() {
        assertThatThrownBy(() -> new CsvRecord("items.csv", COLUMNS, List.of("only-name"), 3))
                .isInstanceOf(StorageException.class)
                .hasMessage("Malformed row in items.csv at line 3");
    }

    @Test
    void returnsTextByColumnName() {
        CsvRecord record = new CsvRecord("items.csv", COLUMNS, List.of("apple", "5"), 2);

        assertThat(record.text("name")).isEqualTo("apple");
    }

    @Test
    void parsesRequiredValueAfterStrippingWhitespace() {
        CsvRecord record = new CsvRecord("items.csv", COLUMNS, List.of("apple", " 5 "), 2);

        assertThat(record.<Integer>required("count", Integer::valueOf)).isEqualTo(5);
    }

    @Test
    void optionalValueIsNullWhenBlank() {
        CsvRecord record = new CsvRecord("items.csv", COLUMNS, List.of("apple", "  "), 2);

        assertThat(record.<Integer>optional("count", Integer::valueOf)).isNull();
    }

    @Test
    void reportsLocationOfMalformedValue() {
        CsvRecord record = new CsvRecord("items.csv", COLUMNS, List.of("apple", "five"), 4);

        assertThatThrownBy(() -> record.required("count", Integer::valueOf))
                .isInstanceOf(StorageException.class)
                .hasMessage("Malformed value in items.csv at line 4, column 'count': invalid token 'five'");
    }

    @Test
    void rejectsUnknownColumn() {
        CsvRecord record = new CsvRecord("items.csv", COLUMNS, List.of("apple", "5"), 2);

        assertThatThrownBy(() -> record.text("price"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("price");
    }
}
