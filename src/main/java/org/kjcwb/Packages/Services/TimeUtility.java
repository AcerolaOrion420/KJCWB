package org.kjcwb.Packages.Services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class TimeUtility {


    public long dateToMilliseconds(String date)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate formatdate = LocalDate.parse(date, formatter);
        return formatdate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public String dateFormatter(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return date.format(formatter);
    }

    public String timeFormatter(long milliseconds)
    {
        LocalTime time = LocalTime.ofNanoOfDay(milliseconds * 1_000_000); // Convert milliseconds to nanoseconds
        // Define a DateTimeFormatter for the desired format "hh:mm:ss a"
        DateTimeFormatter timeformatter = DateTimeFormatter.ofPattern("hh:mm a");

        return time.format(timeformatter);
    }

    //////////
    public long todayDateMilliseconds() {
        LocalDate today = LocalDate.now();
        return today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public long currentDateAndTimeMillis() {
        LocalDateTime now = LocalDateTime.now();
        return now.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public long timeToMilliseconds(String time) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");
        try {
            Date dateObj = sdf.parse(time);
            long milliseconds = dateObj.getTime() - sdf.parse("12:00 AM").getTime();
            return milliseconds;
        } catch (ParseException e) {
            e.printStackTrace();
            return -1;
        }
    }
}