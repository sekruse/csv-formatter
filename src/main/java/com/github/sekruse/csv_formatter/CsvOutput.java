package com.github.sekruse.csv_formatter;

import java.io.IOException;
import java.util.List;

/**
 * Wraps actual CSV writers.
 */
public interface CsvOutput extends AutoCloseable {

    /**
     * Write a CSV record.
     *
     * @param fields the fields of the CSV record
     * @throws IOException
     */
    void write(List<String> fields) throws IOException;

    @Override
    void close() throws IOException;
}
