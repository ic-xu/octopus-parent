package io.octopus.kernel.kernel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Parses the acl configuration file. If a line starts with # it's comment. Blank lines are skipped.
 * The format is "topic [read|write|readwrite] {topic name}"
 */
public final class ACLFileParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ACLFileParser.class);

    /**
     * Parse the configuration from file.
     *
     * @param file
     *            to parse
     * @return the collector of authorizations form reader passed into.
     * @throws ParseException
     *             if the format is not compliant.
     */
    public static AuthorizationsCollector parse(File file) throws ParseException {
        if (file == null) {
            LOGGER.warn("parsing NULL file, so fallback on default configuration!");
            return AuthorizationsCollector.emptyImmutableCollector();
        }
        if (!file.exists()) {
            LOGGER.warn(
                    String.format(
                            "parsing not existing file %s, so fallback on default configuration!",
                            file.getAbsolutePath()));
            return AuthorizationsCollector.emptyImmutableCollector();
        }
        try {
            Reader reader = Files.newBufferedReader(file.toPath(), UTF_8);
            return parse(reader);
        } catch (IOException fex) {
            LOGGER.warn(
                    String.format(
                            "parsing not existing file %s, so fallback on default configuration!",
                            file.getAbsolutePath()),
                    fex);
            return AuthorizationsCollector.emptyImmutableCollector();
        }
    }

    /**
     * Parse the ACL configuration file
     *
     * @param reader
     *            to parse
     * @return the collector of authorizations form reader passed into.
     * @throws ParseException
     *             if the format is not compliant.
     */
    public static AuthorizationsCollector parse(Reader reader) throws ParseException {
        if (reader == null) {
            // just log and return default properties
            LOGGER.warn("parsing NULL reader, so fallback on default configuration!");
            return AuthorizationsCollector.emptyImmutableCollector();
        }

        BufferedReader br = new BufferedReader(reader);
        String line;
        AuthorizationsCollector collector = new AuthorizationsCollector();

        Pattern emptyLine = Pattern.compile("^\\s*$");
        Pattern commentLine = Pattern.compile("^#.*"); // As spec, comment lines should start with '#'
        Pattern invalidCommentLine = Pattern.compile("^\\s*#.*");
        // This pattern has a dependency on filtering `commentLine`.
        Pattern endLineComment = Pattern.compile("^([\\w\\s\\/\\+]+#?)(\\s*#.*)$");
        Matcher endLineCommentMatcher;

        try {
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || emptyLine.matcher(line).matches() || commentLine.matcher(line).matches()) {
                    // skip it's a black line or comment
                    continue;
                } else if (invalidCommentLine.matcher(line).matches()) {
                    // it's a malformed comment
                    int commentMarker = line.indexOf('#');
                    throw new ParseException(line, commentMarker);
                }

                endLineCommentMatcher = endLineComment.matcher(line);
                if (endLineCommentMatcher.matches()) {
                    line = endLineCommentMatcher.group(1);
                }

                collector.parse(line);
            }
        } catch (IOException ex) {
            throw new ParseException("Failed to read", 1);
        }
        return collector;
    }

    private ACLFileParser() {
    }
}
