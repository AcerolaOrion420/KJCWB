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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;


public class CounsellorUpcomingSession {

    private final MongoDatabase database;
    private static final Logger LOGGER = LoggerFactory.getLogger(CounsellorUpcomingSession.class);
    private final TimeUtility timeUtility = new TimeUtility();

    public CounsellorUpcomingSession() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            LOGGER.info("CounsellorUpcomingSession class initialized and connected to DB");
        } else {
            LOGGER.error("CounsellorUpcomingSession class failed to connect to the database");
        }
    }

    public void getUpcomingSession(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Invalid request body")));
            return;
        }

        String counsellor_id = ctx.user().principal().getString("id");
        String receivedDate = requestBody.getString("start_date");

        if (counsellor_id == null || receivedDate == null) {
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
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Internal Server Error")));
            return;
        }

        List<Document> upcomingSessionList = new ArrayList<>();
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Booked_slots");
            //Document query = new Document("counselor_id", counsellorId).append("date_mils", receivedDateMillis).append("slot_status","pending");
            Document query = new Document("counsellor_id", counsellor_id)
                    .append("date_milliseconds", receivedDateMillis)
                    .append("slot_status", new Document("$ne", "Cancelled"));


            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
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
        } else {
            LOGGER.error("CounsellorUpcomingSession class failed to connect to the database.");
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Internal Server Error")));
        }
    }

    private Document processDocument(Document doc) {
        long milliseconds = doc.getLong("date_milliseconds");
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneOffset.UTC);
        String formattedDate = timeUtility.dateFormatter(date);

        long slotStartTimeMillis = doc.getInteger("slot_start_time_milliseconds");
        long slotEndTimeMillis = doc.getInteger("slot_end_time_milliseconds");

        String formattedStartTime = timeUtility.timeFormatter(slotStartTimeMillis);
        String formattedEndTime = timeUtility.timeFormatter(slotEndTimeMillis);

        Document booked = new Document("student_id", doc.getString("student_id"));

        MongoCollection<Document> student = database.getCollection("Student");
        Document query = new Document("_id", doc.getString("student_id"));

        try (MongoCursor<Document> cursor = student.find(query).iterator()) {
            if (cursor.hasNext()) {
                Document studentDoc = cursor.next();
                booked.append("student_name", studentDoc.getString("name"));
                booked.append("course", studentDoc.getString("course"));
                booked.append("sem", studentDoc.getString("sem"));
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching student details: {}", e.getMessage(), e);
        }

        booked.append("date", formattedDate);
        booked.append("start_time", formattedStartTime);
        booked.append("end_time", formattedEndTime);
        booked.append("_id", doc.getString("_id"));
        booked.append("slot_status",doc.getString("slot_status"));

        return booked;
    }
}