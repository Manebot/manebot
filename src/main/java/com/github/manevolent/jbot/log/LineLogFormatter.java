package com.github.manevolent.jbot.log;

import com.github.manevolent.jbot.virtual.Virtual;
import com.github.manevolent.jbot.virtual.VirtualProcess;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LineLogFormatter extends Formatter {
    private static final String lineSeparator = "\n";

    private final DateFormat dateFormat;

    public LineLogFormatter(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
    }

    public LineLogFormatter() {
        this(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    public synchronized String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();

        String message = formatMessage(record);

        sb.append("[").append(dateFormat.format(Calendar.getInstance().getTime())).append("] ");

        sb.append("[").append(record.getLoggerName()).append("] ");

        VirtualProcess currentProcess = Virtual.getInstance().currentProcess();
        sb.append("[").append(currentProcess != null ?
                currentProcess.getName() :
                ("anon-" + Thread.currentThread().getId())).append("] ");

        sb.append("[").append(record.getLevel().getLocalizedName()).append("] ");

        sb.append(message);

        sb.append(lineSeparator);

        if (record.getThrown() != null) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                sb.append(sw.toString());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        return sb.toString();
    }
}