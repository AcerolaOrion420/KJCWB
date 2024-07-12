package org.kjcwb.Packages.Student;

import org.kjcwb.Packages.Services.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StudentUpcomingSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudentUpcomingSession.class);
    private static final TimeUtility timeUtility = new TimeUtility();

    public static void getUpcomingSession(RoutingContext ctx) {

        String studentId = ctx.user().principal().getString("id");
        System.out.println(studentId);
        if (studentId == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Missing Student ID")));
            return;
        }

        List<Document> upcomingSessionList = new ArrayList<>();

        LocalDate today = LocalDate.now();
        long todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        LocalTime currentTime = LocalTime.now();

        int timerange = getTimeRange();
        LocalTime adjustedTime = currentTime.minusMinutes(timerange);
        LocalDateTime currentDateTime = LocalDateTime.of(today, adjustedTime);
        long currentMillis = currentDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        MongoService.initialize("mongodb://localhost:27017", "admin", "Booked_Slots");
        Document query = new Document("student_id", studentId)
            .append("date_mils", new Document("$gte", todayMillis));


        try {
            List<Document> documents = MongoService.findall(query);
            for (Document doc : documents) {
                processDocument(doc, currentMillis, upcomingSessionList);
            }

            if (upcomingSessionList.isEmpty()) {
                ctx.response().putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(Collections.singletonMap("message", "No slots have been booked!")));
            } else {
                ctx.response().putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(upcomingSessionList));
            }

        }
        catch (Exception e) {
            LOGGER.error("Error fetching sessions: {}", e.getMessage(), e);
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Internal Server Error")));
        }MongoService.close();
    }

    private static void processDocument(Document doc, long currentMillis, List<Document> upcomingSessionList) {

        long milliseconds = doc.getLong("date_mils");
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneOffset.UTC);
        String formattedDate = timeUtility.dateFormatter(date);

        long dateMillis = doc.getLong("date_mils");
        long slotStartTimeMillis = doc.getInteger("slot_s_time_m");
        long slotEndTimeMillis = doc.getInteger("slot_e_time_m");

        String formattedStartTime = timeUtility.timeFormatter(slotStartTimeMillis);
        String formattedEndTime = timeUtility.timeFormatter(slotEndTimeMillis);

        long combinedMillis = dateMillis + slotEndTimeMillis;

        if (currentMillis <= combinedMillis) {
            Document booked = new Document("student_id", doc.getString("student_id"))
                    .append("counselor_id", doc.getString("counselor_id"))
                    .append("_id", doc.getString("_id"));
            String counselorId = doc.getString("counselor_id");

            MongoService.initialize("mongodb://localhost:27017", "admin", "Counsellor");
            Document counsellorDoc = MongoService.find("_id", counselorId);
            if (counsellorDoc != null) {
                booked.append("counselor_name", counsellorDoc.getString("name"));
            }

            booked.append("date", formattedDate);
            booked.append("slot_s_time_m", formattedStartTime);
            booked.append("slot_e_time_m", formattedEndTime);
            upcomingSessionList.add(booked);
        }
        MongoService.close();
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
