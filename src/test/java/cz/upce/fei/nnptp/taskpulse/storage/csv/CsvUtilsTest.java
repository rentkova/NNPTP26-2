package cz.upce.fei.nnptp.taskpulse.storage.csv;

import cz.upce.fei.nnptp.taskpulse.storage.StorageException;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CsvUtilsTest {
    @Test
    void rejectsUnclosedQuoteAtEndOfCSVFile() {
        String csv = "name,description\nProject,\"unfinished";
        
        assertThatThrownBy(() -> CsvUtils.parse(new StringReader(csv)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("unclosed double quote");
    }
    
    @Test
    void acceptsClosedQuoteAtEndOfCSVFile() {
        String csv = "name,description\nProject,\"finished\"";
        
        assertThatCode(() -> CsvUtils.parse(new StringReader(csv)))
                .doesNotThrowAnyException();
    }

    @Test
    void formatRowEscapesOnlyFieldsThatNeedQuoting() {
        String row = CsvUtils.formatRow(List.of("plain", "a,b", "say \"hi\"", ""));

        assertThat(row).isEqualTo("plain,\"a,b\",\"say \"\"hi\"\"\",");
    }
}