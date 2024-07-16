package org.kjcwb.Packages.Counsellor;
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

public class CounsellorUpcomingSessions {
    private static final Logger LOGGER = LoggerFactory.getLogger(CounsellorUpcomingSessions.class);
    private static final TimeUtility timeUtility = new TimeUtility();

    public static void getUpcomingSession(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Invalid request body")));
            return;
        }

        String counsellorId = ctx.user().principal().getString("id");
        String receivedDate = requestBody.getString("start_date");

        if (counsellorId == null || receivedDate == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Missing required fields")));
            return;
        }

        long receivedDateMillis;
        try {
            receivedDateMillis = timeUtility.dateToMilliseconds(receivedDate);
        } catch (Exception e) {
            LOGGER.error("Error parsing date: {}", e.getMessage(), e);
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Invalid date format")));
            return;
        }
        MongoService.initialize( "Booked_Slots");
        List<Document> upcomingSessionList = new ArrayList<>();
        Document query = new Document("counselor_id", counsellorId).append("date_mils", receivedDateMillis);

        try {
            List<Document> documents = MongoService.findall(query);
            for (Document doc : documents) {
                Document booked = processDocument(doc);
                upcomingSessionList.add(booked);
            }

            if (upcomingSessionList.isEmpty()) {
                ctx.response().putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(Collections.singletonMap("message", "No Slots Booked for the selected date!")));
            } else {
                ctx.response().putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(upcomingSessionList));
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching counsellors: {}", e.getMessage(), e);
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Internal Server Error")));
        }
    }

    private static Document processDocument(Document doc) {
        MongoService.initialize(  "Student");
        long milliseconds = doc.getLong("date_mils");
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneOffset.UTC);
        String formattedDate = timeUtility.dateFormatter(date);

        long slotStartTimeMillis = doc.getInteger("slot_s_time_m");
        long slotEndTimeMillis = doc.getInteger("slot_e_time_m");

        String formattedStartTime = timeUtility.timeFormatter(slotStartTimeMillis);
        String formattedEndTime = timeUtility.timeFormatter(slotEndTimeMillis);

        Document booked = new Document("student_id", doc.getString("student_id"));

        Document studentDoc = MongoService.find("_id", doc.getString("student_id"));
        if (studentDoc != null) {
            booked.append("student_name", studentDoc.getString("name"));
            booked.append("course", studentDoc.getString("course"));
            booked.append("sem", studentDoc.getString("sem"));
        }

        booked.append("date", formattedDate);
        booked.append("slot_s_time_m", formattedStartTime);
        booked.append("slot_e_time_m", formattedEndTime);
        booked.append("_id", doc.getString("_id"));

        return booked;
    }
}