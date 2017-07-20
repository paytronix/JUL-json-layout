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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;

import java.util.TimeZone;
import org.apache.commons.lang.time.FastDateFormat;


/**
 *
 */
public class LogstashUtilFormatter extends Formatter {

    private static final JsonBuilderFactory BUILDER =
            Json.createBuilderFactory(null);
    private static String hostName;
    private static final String[] tags = System.getProperty(
            "net.logstash.logging.formatter.LogstashUtilFormatter.tags", "UNKNOWN").split(",");

    static final FastDateFormat ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");

    static String dateFormat(long timestamp) {
        return ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS.format(timestamp);
    }

    static {
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "unknown-host";
        }
    }

    @Override
    public final String format(final LogRecord record) {
        final String container = tags[0];
        JsonObject empty_json = BUILDER.createObjectBuilder().build();

        JsonObjectBuilder builder = BUILDER.createObjectBuilder();
        builder.add("thread_name", Thread.currentThread().getName());
        builder.add("message", formatMessage(record));
        builder.add("timestamp", dateFormat(record.getMillis()));
        builder.add("level", record.getLevel().toString());
        builder.add("mdc", empty_json);
        builder.add("container", container);
        builder.add("logger_name", record.getLoggerName());
        builder.add("source_host", hostName);
        addThrowableInfo(record, builder);
        return builder.build().toString() + "\n";
    }

    @Override
    public synchronized String formatMessage(final LogRecord record) {
        String message = super.formatMessage(record);

        try {
            final Object parameters[] = record.getParameters();
            if (message == record.getMessage() && parameters != null && parameters.length > 0) {
                message = String.format(message, parameters);
            }
        } catch (Exception ex) {
        }

        return message;
    }

    /**
     * Format the stackstrace.
     *
     * @param record the logrecord which contains the stacktrace
     * @param builder the json object builder to append
     */
    final void addThrowableInfo(final LogRecord record, final JsonObjectBuilder builder) {
        if (record.getThrown() != null) {
            if (record.getSourceClassName() != null) {
                builder.add("exception_class",
                        record.getThrown().getClass().getName());
            }
            if (record.getThrown().getMessage() != null) {
                builder.add("exception_message",
                        record.getThrown().getMessage());
            }
            addStacktraceElements(record, builder);
        }
    }

    private void addStacktraceElements(final LogRecord record, final JsonObjectBuilder builder) {
        final StringWriter sw = new StringWriter();
        record.getThrown().printStackTrace(new PrintWriter(sw));
        builder.add("stacktrace", sw.toString());
    }
}
