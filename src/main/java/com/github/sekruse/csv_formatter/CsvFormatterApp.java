package com.github.sekruse.csv_formatter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.csv.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * The main class.
 */
public class CsvFormatterApp {

    public static void main(String[] args) throws IOException {
        final Parameters parameters = parseParameters(args);

        Character ouputEscape = Parameters.parseConfigChar(parameters.outputEscape);
        if (parameters.isFlatten && ouputEscape == null) {
            throw new IllegalArgumentException("Cannot flatten lines without an escape character.");
        }

        int recordNumber = 0;
        int numFields = -1;
        try (CSVParser parser = parameters.createCsvParser()) {
            try (CSVPrinter printer = parameters.createCsvPrinter()) {
                ReadLoop:
                for (CSVRecord csvRecord : parser) {
                    if (++recordNumber == 1) {
                        numFields = csvRecord.size();
                    } else if (csvRecord.size() != numFields) {
                        String message = String.format("Record %d has %d fields (expected %d): %s",
                                recordNumber, csvRecord.size(), numFields, csvRecord
                        );
                        switch (parameters.cleaningStrategy) {
                            case "keep":
                                System.err.println(message);
                                break;
                            case "drop":
                                System.err.println(message);
                                continue ReadLoop;
                            case "fail":
                                throw new RuntimeException(message);
                        }
                    }
                    if (parameters.isFlatten) {
                        List<String> flatRecord = new ArrayList<>(csvRecord.size());
                        for (String field : csvRecord) {
                            flatRecord.add(flatten(field, ouputEscape));
                        }
                        printer.printRecord(flatRecord);
                    } else {
                        printer.printRecord(csvRecord);
                    }
                }
            }
        }
    }

    /**
     * Flatten a CSV field by replacing tabs, line feeds, and new lines.
     *
     * @param field the field to flatten
     * @return the flattened field
     */
    private static String flatten(String field, char escape) {
        if (field == null) return null;
        int pos = -1;
        StringBuilder sb = null;
        while (++pos < field.length()) {
            char c = field.charAt(pos);
            switch (c) {
                case '\n':
                case '\r':
                case '\t':
                    if (sb == null) {
                        sb = new StringBuilder(field.length() * 2);
                        sb.append(field, 0, pos);
                    }
                    sb.append(escape);
                    switch (c) {
                        case '\n':
                            sb.append('n');
                            break;
                        case '\r':
                            sb.append('r');
                            break;
                        case '\t':
                            sb.append('t');
                            break;
                    }
                    break;
                default:
                    if (sb != null) sb.append(c);
            }
        }
        return sb == null ? field : sb.toString();
    }

    /**
     * Parse the command line arguments.
     *
     * @param args the command line arguments
     * @return a {@link Parameters} object with the parsed arguments
     */
    private static Parameters parseParameters(String[] args) {
        Parameters parameters = new Parameters();
        JCommander jcommander = JCommander.newBuilder()
                .addObject(parameters)
                .build();
        try {
            jcommander.parse(args);
        } catch (com.beust.jcommander.ParameterException e) {
            System.err.println("Could not parse parameters: " + e.getMessage());
            StringBuilder sb = new StringBuilder();
            jcommander.usage(sb);
            System.err.println(sb.toString());
            System.exit(1);
        }

        if (parameters.isHelp) {
            jcommander.usage();
            System.exit(0);
        }

        return parameters;
    }


    /**
     * Parameters for the {@link CsvFormatterApp}.
     */
    public static class Parameters {

        @Parameter(description = "input file; if not specified, the stdin will serve as input")
        public List<String> inputFiles = new ArrayList<>();

        @Parameter(names = {"-f", "--input-field-separator"}, description = "field separator of the input file")
        public String inputFieldSeparator = "comma";

        @Parameter(names = {"-r", "--input-record-separator"}, description = "record separator of the input file")
        public String inputRecordSeparator = "newline";

        @Parameter(names = {"-q", "--input-quote"}, description = "quote char of the input file")
        public String inputQuote = "double";

        @Parameter(names = {"-m", "--input-quote-mode"}, description = "quote mode of the input file (all, none, notnull, text, minimal)")
        public String inputQuoteMode = "minimal";

        @Parameter(names = {"-x", "--input-escape"}, description = "escape character of the input file")
        public String inputEscape = null;

        @Parameter(names = {"-e", "--input-encoding"}, description = "encoding of the input file")
        public String inputEncoding = "UTF-8";

        @Parameter(names = {"-i", "--ignore-surrounding-space"}, description = "ignore surrounding spaces in the input file")
        public boolean isIgnoreSurroundingSpace = false;

        @Parameter(names = {"-l", "--ignore-empty-lines"}, description = "ignore empty lines in the input file")
        public boolean isIgnoreEmptyLines = false;

        @Parameter(names = {"-O", "--output"}, description = "output file; if not specified, the stdout will serve as output")
        public String outputFile = null;

        @Parameter(names = {"-F", "--output-field-separator"}, description = "field separator of the output file")
        public String outputFieldSeparator = "comma";

        @Parameter(names = {"-R", "--output-record-separator"}, description = "record separator of the output file")
        public String outputRecordSeparator = "newline";

        @Parameter(names = {"-Q", "--output-quote"}, description = "quote char of the output file")
        public String outputQuote = "double";

        @Parameter(names = {"-M", "--output-quote-mode"}, description = "quote mode of the output file (all, none, notnull, text, minimal)")
        public String outputQuoteMode = "all";

        @Parameter(names = {"-X", "--output-escape"}, description = "escape character of the output file")
        public String outputEscape = null;

        @Parameter(names = {"-E", "--output-encoding"}, description = "encoding of the output file")
        public String outputEncoding = "UTF-8";

        @Parameter(names = {"--cleaning-strategy"}, description = "what to do with too small or large records (keep, drop, fail)")
        public String cleaningStrategy = "fail";

        @Parameter(names = {"--flatten"}, description = "whether to replace line feeds and new lines with escape characters")
        public boolean isFlatten;

        @Parameter(names = {"-h", "--help"}, description = "show the help and exit")
        public boolean isHelp;

        /**
         * Creates a {@link CSVParser} according to the settings of this instance.
         *
         * @return the {@link CSVParser}
         */
        public CSVParser createCsvParser() throws IOException {
            final Charset inputCharset = Charset.forName(this.inputEncoding);
            final Reader reader;
            if (this.inputFiles.isEmpty()) {
                reader = new InputStreamReader(System.in, inputCharset);
            } else if (this.inputFiles.size() == 1) {
                reader = new InputStreamReader(new FileInputStream(this.inputFiles.get(0)), inputCharset);
            } else {
                throw new IllegalArgumentException("Multiple input files are not supported.");
            }
            CSVFormat inputCsvFormat = createCsvFormat(
                    this.inputFieldSeparator,
                    this.inputRecordSeparator,
                    this.inputQuote,
                    this.inputQuoteMode,
                    this.inputEscape,
                    this.isIgnoreSurroundingSpace,
                    this.isIgnoreEmptyLines
            );
            CSVParser parser;
            try {
                parser = new CSVParser(reader, inputCsvFormat);
            } catch (IOException e) {
                reader.close();
                throw e;
            }
            return parser;
        }

        /**
         * Creates a {@link CSVPrinter} according to the settings of this instance.
         *
         * @return the {@link CSVPrinter}
         */
        public CSVPrinter createCsvPrinter() throws IOException {
            final Charset outputCharset = Charset.forName(this.outputEncoding);
            final Writer writer;
            if (this.outputFile == null) {
                writer = new OutputStreamWriter(System.out, outputCharset);
            } else {
                writer = new OutputStreamWriter(new FileOutputStream(this.outputFile), outputCharset);
            }
            CSVFormat outputCsvFormat = createCsvFormat(
                    this.outputFieldSeparator,
                    this.outputRecordSeparator,
                    this.outputQuote,
                    this.outputQuoteMode,
                    this.outputEscape,
                    true,
                    true
            );
            CSVPrinter printer;
            try {
                printer = new CSVPrinter(writer, outputCsvFormat);
            } catch (IOException e) {
                writer.close();
                throw e;
            }
            return printer;
        }

        /**
         * Creates a new {@link CSVFormat}.
         *
         * @param fieldSeparatorConfig     configuration of the field separator
         * @param recordSeparatorConfig    configuration of the record separator
         * @param quoteConfig              configuration of the quote character
         * @param quoteModeConfig          configuration of the quote mode
         * @param escapeConfig             configuration of the escape character
         * @param isIgnoreSurroundingSpace whether to ignore spaces surrouding the quote characters of a field
         * @param isIgnoreEmptyLines       whether to ignore empty lines
         * @return
         */
        private static CSVFormat createCsvFormat(String fieldSeparatorConfig,
                                                 String recordSeparatorConfig,
                                                 String quoteConfig,
                                                 String quoteModeConfig,
                                                 String escapeConfig,
                                                 boolean isIgnoreSurroundingSpace,
                                                 boolean isIgnoreEmptyLines) {
            return CSVFormat.newFormat(parseConfigChar(fieldSeparatorConfig))
                    .withRecordSeparator(parseConfigString(recordSeparatorConfig))
                    .withQuote(parseConfigChar(quoteConfig))
                    .withQuoteMode(parseQuoteMode(quoteModeConfig))
                    .withEscape(parseConfigChar(escapeConfig))
                    .withIgnoreSurroundingSpaces(isIgnoreSurroundingSpace)
                    .withIgnoreEmptyLines(isIgnoreEmptyLines);
        }

        /**
         * Parse a configuration {@link String} for characters and the like.
         *
         * @param config the configuration {@link String}
         * @return the parsed {@link String}
         */
        private static String parseConfigString(String config) {
            if (config == null) return null;
            switch (config) {
                case "null":
                    return null;

                case "\\n":
                case "newline":
                case "nl":
                    return "\n";

                case "\\r":
                case "linefeed":
                case "lf":
                    return "\r";

                case "\\r\\n":
                case "linefeed+newline":
                case "lf+nl":
                    return "\r\n";

                case "comma":
                    return ",";

                case "semicolon":
                    return ";";

                case "double":
                case "doublequote":
                case "\\\"":
                    return "\"";

                case "single":
                case "singlequote":
                case "\\'":
                    return "'";

                case "backslash":
                case "\\":
                case "\\\\":
                    return "\\";

                case "tab":
                case "\\t":
                    return "\t";

                default:
                    return config;
            }
        }

        /**
         * Parse a configuration {@link String} for a {@link QuoteMode}.
         *
         * @param config the configuration {@link String}
         * @return the parsed {@link QuoteMode}
         */
        private static QuoteMode parseQuoteMode(String config) {
            switch (config) {
                case "minimal":
                    return QuoteMode.MINIMAL;
                case "notnull":
                    return QuoteMode.ALL_NON_NULL;
                case "all":
                    return QuoteMode.ALL;
                case "text":
                    return QuoteMode.NON_NUMERIC;
                case "none":
                    return QuoteMode.NONE;
                default:
                    throw new IllegalArgumentException("Unknown quote mode.");
            }
        }

        /**
         * Parse a configuration {@link Character}.
         *
         * @param config the configuration {@link String}
         * @return the parsed {@link Character}
         */
        private static Character parseConfigChar(String config) {
            String str = parseConfigString(config);
            if (str == null) return null;
            else if (str.length() == 1) return str.charAt(0);
            throw new IllegalArgumentException(String.format("Could not map \"%s\" to a character.", config));
        }

    }


}
