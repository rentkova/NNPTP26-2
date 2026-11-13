package cz.upce.fei.nnptp.taskpulse.storage.csv;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.List;

final class CsvMapperTestSupport {
    private CsvMapperTestSupport() {
    }

    static <T> T fromValues(CsvEntityMapper<T> mapper, int lineNumber, String... values) {
        return mapper.fromRecord(new CsvRecord(mapper.fileName(), mapper.columns(), List.of(values), lineNumber));
    }

    static <T> T roundTrip(CsvEntityMapper<T> mapper, T value) {
        try {
            String line = CsvUtils.formatRow(mapper.toFields(value));
            List<String> parsed = CsvUtils.parse(new StringReader(line)).get(0);
            return mapper.fromRecord(new CsvRecord(mapper.fileName(), mapper.columns(), parsed, 2));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
