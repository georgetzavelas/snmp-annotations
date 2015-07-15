package com.tzavelas.snmp;

import java.util.Calendar;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import com.google.common.base.Throwables;
import com.google.common.base.Throwables;

public class VerySimpleFormatterWithTimestamp extends Formatter
{
    private static final String LINE_SEPERATOR = System.getProperty( "line.separator" );

    @Override
    public String format( LogRecord record )
    {
        String exceptionMsg = "";
        if( record.getThrown() != null){
            exceptionMsg = LINE_SEPERATOR + Throwables.getStackTraceAsString( record.getThrown() );
        }
        return String.format(
                "%1$td-%1$tm-%1$ty %1$tH:%1$tM:%1$tS:%2$s:%3$s%4$s%5$s",
                Calendar.getInstance(),
                record.getLevel(),
                record.getMessage(),
                exceptionMsg,
                LINE_SEPERATOR);
    }
}
