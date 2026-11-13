package cz.upce.fei.nnptp.taskpulse.storage.csv;

import java.util.List;

/**
 * Converts between a domain value and its CSV representation.
 * Implementations are stateless and know nothing about file I/O or relationships between entities.
 *
 * @param <T> mapped domain type
 */
interface CsvEntityMapper<T> {

    String fileName();

    List<String> columns();

    T fromRecord(CsvRecord record);

    List<String> toFields(T value);
}
