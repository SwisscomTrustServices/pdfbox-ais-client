package com.swisscom.ais.client;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestUtils {

    public static final String testConfigPath = getResourceAbsPath("/test-config.properties");
    public static final String testPdfFilePath = getResourceAbsPath("/test-doc.pdf");
    public static final Calendar signatureSignDate;
    public static final Calendar signatureSignDatePlus3Minutes;
    public static final LocalDateTime testLocalDateTime1 = LocalDateTime.of(2000, 4, 10, 12, 30, 59);
    public static final LocalDateTime testLocalDateTime2 = LocalDateTime.of(2001, 8, 11, 16, 15, 29);
    private static final String targetFilesPath = Paths.get("target/test-classes").toAbsolutePath().toString();
    private static final String rootFilesPath = Paths.get("").toAbsolutePath().toString();
    public static final String signedDocPath1 = rootFilesPath + "/____on2000-4-10 at 12-30-59.pdf";
    public static final String signedDocPath2 = rootFilesPath + "/____on2001-8-11 at 16-15-29.pdf";
    public static final String signedMultiDocsPath1 = targetFilesPath + "/test-doc-signed-20000410-123059.pdf";
    public static final String signedMultiDocsPath2 = targetFilesPath + "/test-doc-signed-20010811-161529.pdf";
    public static final UUID testUuid1 = UUID.nameUUIDFromBytes("test-random-uuid-1".getBytes());
    public static final UUID testUuid2 = UUID.nameUUIDFromBytes("test-random-uuid-2".getBytes());
    public static final UUID testUuid3 = UUID.nameUUIDFromBytes("test-random-uuid-3".getBytes());

    static {
        final Calendar.Builder calendar = new Calendar.Builder();
        calendar.setDate(2010, 6, 17);
        calendar.setTimeOfDay(3, 45, 15, 0);
        signatureSignDate = calendar.build();
        final Calendar.Builder calendarPlus3Minutes = new Calendar.Builder();
        calendarPlus3Minutes.setDate(2010, 6, 17);
        calendarPlus3Minutes.setTimeOfDay(3, 48, 15, 0);
        signatureSignDatePlus3Minutes = calendarPlus3Minutes.build();
    }

    public static Calendar getSignatureSignDate() {
        return (Calendar) signatureSignDate.clone();
    }

    public static Calendar getSignatureSignDatePlus3Minutes() {
        return (Calendar) signatureSignDatePlus3Minutes.clone();
    }

    private static String getResourceAbsPath(final String resourcePath) {
        return CliIT.class.getResource(resourcePath).getPath();
    }

    public static class TestConsoleStream extends PrintStream {

        private final Pattern authUrlLogPattern = Pattern.compile("^click url to retrieve JWT code: (.*)$");
        private final Pattern finalResultPattern = Pattern.compile("^Final result: (.*)$");
        private String authUrl;
        private String finalResult;

        public TestConsoleStream() {
            super(new ByteArrayOutputStream());
        }

        public String getAuthUrl() {
            return authUrl;
        }

        public String getFinalResult() {
            return finalResult;
        }

        @Override
        public void println(String log) {
            super.println(log);
            final Matcher authUrlMatcher = authUrlLogPattern.matcher(log);
            if (authUrlMatcher.matches()) {
                authUrl = authUrlMatcher.group(1);
                return;
            }
            final Matcher finalResultMatcher = finalResultPattern.matcher(log);
            if (finalResultMatcher.matches()) {
                finalResult = finalResultMatcher.group(1);
                return;
            }
        }
    }
}
