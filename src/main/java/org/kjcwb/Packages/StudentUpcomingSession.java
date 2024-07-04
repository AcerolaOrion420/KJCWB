package org.kjcwb.Packages;


import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.kjcwb.Packages.Services.MongoService;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StudentUpcomingSession {


    public static void getUpcomingsession(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.emptyList()));
            return;
        }
        String student_id = requestBody.getString("student_id");
        System.out.println("student_id received: " + student_id);

        List<Document> upcomingSessionList = new ArrayList<>();
        try {
            LocalDate today = LocalDate.now();
            long todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            LocalTime currenttime = LocalTime.now();

            // minus 30 minutes from the current time since 30 minutes in DB
            int timerange = getTimeRange();
            LocalTime adjustedTime = currenttime.minusMinutes(timerange);
            LocalDateTime currentDateTime = LocalDateTime.of(today, adjustedTime);
            long currentMillis = currentDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
            MongoService.initialize("mongodb://localhost:27017", "admin", "Booked_Slots");

            // Query to find documents where "student_id" matches and date is greater than today
            Document query = new Document("student_id", student_id)
                    .append("date_mils", new Document("$gte", todayMillis));
            System.out.println("query: " + query);

            List<Document> results = MongoService.findall(query);
            for (Document doc : results) {
                System.out.println(doc);

                long milliseconds = doc.getLong("date_mils");
                LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneOffset.UTC);

                // Define a DateTimeFormatter for the desired format "dd/mm/yyyy"
                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .appendValue(ChronoField.DAY_OF_MONTH, 2) // Day of month as two digits (dd)
                        .appendLiteral('/')
                        .appendValue(ChronoField.MONTH_OF_YEAR, 2) // Month as two digits (mm)
                        .appendLiteral('/')
                        .appendValue(ChronoField.YEAR, 4) // Year as four digits (yyyy)
                        .toFormatter();

                // Format LocalDateTime to String
                String formattedDate = date.format(formatter);

                // Print the formatted date
                System.out.println("Formatted Date: " + formattedDate);
                long dateMillis = doc.getLong("date_mils");
                long slotStartTimeMillis = doc.getInteger("slot_s_time_m");
                long slotEndTimeMillis = doc.getInteger("slot_e_time_m");

                // Convert milliseconds to LocalTime
                LocalTime starttime = LocalTime.ofNanoOfDay(slotStartTimeMillis * 1_000_000); // Convert milliseconds to nanoseconds
                // Define a DateTimeFormatter for the desired format "HH:mm:ss.SSS"
                DateTimeFormatter timeformatter = new DateTimeFormatterBuilder()
                        .appendValue(ChronoField.HOUR_OF_DAY, 2) // Hour of day (24-hour format) as two digits (HH)
                        .appendLiteral(':')
                        .appendValue(ChronoField.MINUTE_OF_HOUR, 2) // Minute of hour as two digits (mm)
                        .toFormatter();
                // Format LocalTime to String
                String formattedstartTime = starttime.format(timeformatter);

                // Convert milliseconds to LocalTime
                LocalTime endttime = LocalTime.ofNanoOfDay(slotEndTimeMillis * 1_000_000); // Convert milliseconds to nanoseconds
                // Define a DateTimeFormatter for the desired format "HH:mm:ss.SSS"
                DateTimeFormatter endtimeformatter = new DateTimeFormatterBuilder()
                        .appendValue(ChronoField.HOUR_OF_DAY, 2) // Hour of day (24-hour format) as two digits (HH)
                        .appendLiteral(':')
                        .appendValue(ChronoField.MINUTE_OF_HOUR, 2) // Minute of hour as two digits (mm)
                        .toFormatter();
                // Format LocalTime to String
                String formattedendTime = endttime.format(endtimeformatter);

                long combinedMillis = dateMillis + slotEndTimeMillis;

                if (currentMillis <= combinedMillis) {
                    Document booked = new Document("student_id", doc.getString("student_id")).append("counselor_id", doc.getString("counselor_id"));
                    String counselor_id = doc.getString("counselor_id");

                    // Initialize connection to Counsellor collection
                    MongoService.initialize("mongodb://localhost:27017", "admin", "Counsellor");
                    Document query2 = new Document("_id", counselor_id);
                    List<Document> counsellorResults = MongoService.findall(query2);
                    for (Document doc2 : counsellorResults) {
                        booked.append("counselor_id", doc2.getString("Name"));
                    }

                    booked.append("date", formattedDate);
                    booked.append("slot_s_time_m", formattedstartTime);
                    booked.append("slot_e_time_m", formattedendTime);
                    upcomingSessionList.add(booked);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ctx.response().putHeader("content-type", "application/json")
                .end(Json.encodePrettily(upcomingSessionList));
    }

    private static int getTimeRange() {
        try {
            // Initialize connection to Time_Range collection
            MongoService.initialize("mongodb://localhost:27017", "admin", "Time_Range");
            Document result = MongoService.findall(new Document()).stream().findFirst().orElse(null);
            if (result != null) {
                return result.getInteger("range");
            } else {
                System.out.println("No document found in the 'Time_Range' collection.");
                return -1; // Or some other default value or error handling
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1; // Or some other default value or error handling
        }
    }
}
