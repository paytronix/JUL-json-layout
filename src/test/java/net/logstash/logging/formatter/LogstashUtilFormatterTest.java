/*
 * Copyright 2013 karl spies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.logstash.logging.formatter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ListResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class LogstashUtilFormatterTest {

    private static final String EXPECTED_EX_STACKTRACE = "java.lang.Exception: That is an exception\n"
            + "\tat Test.methodTest(Test.class:42)\n"
            + "Caused by: java.lang.Exception: This is the cause\n"
            + "\tat Cause.methodCause(Cause.class:69)\n";

    private LogRecord record = null;
    private LogstashUtilFormatter instance = new LogstashUtilFormatter();
    private String fullLogMessage = null;
    private JsonObjectBuilder builder;
    private Exception ex, cause;
    private static String hostName;

    static {
        System.setProperty("net.logstash.logging.formatter.LogstashUtilFormatter.tags", "foo,bar");
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "unknown-host";
        }
    }

    public static Exception buildException(final String message, final Throwable cause,
            final StackTraceElement...stackTrace) {
        final Exception result = new Exception(message, cause);
        result.setStackTrace(stackTrace);
        return result;
    }

    /**
     *
     */
    @Before
    public void setUp() {
        cause = buildException("This is the cause", null,
                new StackTraceElement("Cause", "methodCause", "Cause.class", 69));

        ex = buildException("That is an exception", cause,
                new StackTraceElement("Test", "methodTest", "Test.class", 42));

        long millis = System.currentTimeMillis();
        record = new LogRecord(Level.ALL, "Junit Test");
        record.setLoggerName(LogstashUtilFormatter.class.getName());
        record.setSourceClassName(LogstashUtilFormatter.class.getName());
        record.setSourceMethodName("testMethod");
        record.setMillis(millis);
        record.setThrown(ex);

        builder = Json.createBuilderFactory(null).createObjectBuilder();
        JsonObject empty_json = Json.createBuilderFactory(null).createObjectBuilder().build();

        builder.add("thread_name", "main");
        builder.add("message", "Junit Test");
        builder.add("timestamp", instance.dateFormat(millis));
        builder.add("level", Level.ALL.toString());
        builder.add("mdc", empty_json);
        builder.add("container", "foo");
        builder.add("logger_name", LogstashUtilFormatter.class.getName());
        builder.add("source_host", hostName);
        builder.add("exception_class", ex.getClass().getName());
        builder.add("exception_message", ex.getMessage());
        builder.add("stacktrace", EXPECTED_EX_STACKTRACE);

        fullLogMessage = builder.build().toString() + "\n";
    }

    /**
     * Test of format method, of class LogstashFormatter.
     */
    @Test
    public void testFormat() {
        String result = instance.format(record);
        assertEquals(fullLogMessage, result);
    }

    /**
     * Test of addThrowableInfo method, of class LogstashFormatter.
     */
    @Test
    public void testAddThrowableInfo() {
        final String expected = Json.createBuilderFactory(null).createObjectBuilder()
            .add("exception_class", ex.getClass().getName())
            .add("exception_message", ex.getMessage())
            .add("stacktrace", EXPECTED_EX_STACKTRACE)
            .build().toString();

        JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
        instance.addThrowableInfo(record, result);
        assertEquals(expected, result.build().toString());
    }

    /**
     * Test of addThrowableInfo method, of class LogstashFormatter.
     */
    @Test
    public void testAddThrowableInfoNoThrowableAttached() {
        JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
        instance.addThrowableInfo(new LogRecord(Level.OFF, hostName), result);
        assertEquals("{}", result.build().toString());
    }

    /**
     * Test of addThrowableInfo method, of class LogstashFormatter.
     */
    @Test
    public void testAddThrowableInfoThrowableAttachedButWithoutSourceClassName() {
        final String expected = Json.createBuilderFactory(null).createObjectBuilder()
                .add("exception_message", ex.getMessage())
                .add("stacktrace", EXPECTED_EX_STACKTRACE)
                .build().toString();

        record.setSourceClassName(null);

        JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
        instance.addThrowableInfo(record, result);
        assertEquals(expected, result.build().toString());
    }

    /**
     * Test of addThrowableInfo method, of class LogstashFormatter.
     */
    @Test
    public void testAddThrowableInfoThrowableAttachedButWithoutMessage() {
        final Exception ex2 = buildException(null, null, new StackTraceElement[0]);
        record.setThrown(ex2);

        final String expected = Json.createBuilderFactory(null).createObjectBuilder()
                .add("exception_class", ex2.getClass().getName())
                .add("stacktrace", "java.lang.Exception\n")
                .build().toString();

        JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
        instance.addThrowableInfo(record, result);
        assertEquals(expected, result.build().toString());
    }

    @Test
    public void testFormatMessageWithSquigglyFormat() {
        record.setMessage("{0} %s");
        record.setParameters(new Object[] { "hi" });
        assertEquals("hi %s", instance.formatMessage(record));
    }

    @Test
    public void testFormatMessageWithSquigglyFormatAndNullParameters() {
        record.setMessage("{0}");
        record.setParameters(null);
        assertEquals("{0}", instance.formatMessage(record));
    }

    @Test
    public void testFormatMessageWithSquigglyFormatAndEmptyParameters() {
        record.setMessage("{0}");
        record.setParameters(new Object[0]);
        assertEquals("{0}", instance.formatMessage(record));
    }

    @Test
    public void testFormatMessageWithBogusSquigglyFormatAndOkPercentFormat() {
        // this will fail the squiggly formatting, and fall back to % formatting
        record.setMessage("{0'}' %s");
        record.setParameters(new Object[] { "hi" });
        assertEquals("{0'}' hi", instance.formatMessage(record));
    }

    @Test
    public void testFormatMessageWithPercentFormat() {
        record.setMessage("%s");
        record.setParameters(new Object[] { "hi" });
        assertEquals("hi", instance.formatMessage(record));
    }

    @Test
    public void testFormatMessageWithPercentFormatAndNullParameters() {
        record.setMessage("%s");
        record.setParameters(null);
        assertEquals("%s", instance.formatMessage(record));
    }

    @Test
    public void testFormatMessageWithPercentFormatAndEmptyParameters() {
        record.setMessage("%s");
        record.setParameters(new Object[0]);
        assertEquals("%s", instance.formatMessage(record));
    }

    @Test
    public void testFormatMessageWithBogusPercentFormat() {
        record.setMessage("%0.5s");
        record.setParameters(new Object[] { "hi" });
        assertEquals("%0.5s", instance.formatMessage(record));
    }
}
