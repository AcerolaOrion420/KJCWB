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

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

public class StudentUpcomingSession {
    private final MongoDatabase database;
    private static final Logger LOGGER = LoggerFactory.getLogger(StudentUpcomingSession.class);
    private final TimeUtility timeUtility = new TimeUtility();

    public StudentUpcomingSession() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            LOGGER.info("StudentUpcomingSession class initialized and connected to DB");
        } else {
            LOGGER.error("StudentUpcomingSession class failed to connect to the database");
        }
    }

    public void getUpcomingSession(RoutingContext ctx) {
        String studentId = ctx.user().principal().getString("id");
        if (studentId == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Missing Session ID")));
            return;
        }

        List<Document> upcomingSessionList = new ArrayList<>();
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Booked_slots");

            LocalDate today = LocalDate.now();
            long todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            LocalTime currentTime = LocalTime.now();

            int timerange = getTimeRange();
            LocalTime adjustedTime = currentTime.minusMinutes(timerange);
            LocalDateTime currentDateTime = LocalDateTime.of(today, adjustedTime);
            long currentMillis = currentDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();

            Document query = new Document("student_id", studentId)
                    .append("slot_status", new Document("$ne", "Cancelled"))
                    .append("date_milliseconds", new Document("$gte", todayMillis));

            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    processDocument(doc, currentMillis, upcomingSessionList);
                }

                if (upcomingSessionList.isEmpty()) {
                    ctx.response().putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(Collections.singletonMap("message", "No slots have been booked!")));
                } else {
                    ctx.response().putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(upcomingSessionList));
                }

            } catch (Exception e) {
                LOGGER.error("Error fetching sessions: {}", e.getMessage(), e);
                ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(Collections.singletonMap("error", "Internal Server Error")));
            }
        } else {
            LOGGER.error("StudentUpcomingSession class failed to connect to the database.");
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Internal Server Error")));
        }
    }

    private void processDocument(Document doc, long currentMillis, List<Document> upcomingSessionList) {
        long milliseconds = doc.getLong("date_milliseconds");
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneOffset.UTC);
        String formattedDate = timeUtility.dateFormatter(date);

        long dateMillis = doc.getLong("date_milliseconds");
        long slotStartTimeMillis = doc.getInteger("slot_start_time_milliseconds");
        long slotEndTimeMillis = doc.getInteger("slot_end_time_milliseconds");

        String formattedStartTime = timeUtility.timeFormatter(slotStartTimeMillis);
        String formattedEndTime = timeUtility.timeFormatter(slotEndTimeMillis);

        long combinedMillis = dateMillis + slotEndTimeMillis;

        if (currentMillis <= combinedMillis) {
            Document booked = new Document("_id",doc.getString("_id"));
            //.append("_id",doc.getString("_id"));
            String counselorId = doc.getString("counsellor_id");

            MongoCollection<Document> counsellorCollection = database.getCollection("Counsellors");
            Document query = new Document("_id", counselorId);

            try (MongoCursor<Document> cursor = counsellorCollection.find(query).iterator()) {
                if (cursor.hasNext()) {
                    Document counsellorDoc = cursor.next();
                    booked.append("counselor_name", counsellorDoc.getString("name"));
                }
            } catch (Exception e) {
                LOGGER.error("Error fetching counsellor details: {}", e.getMessage(), e);
            }

            booked.append("date", formattedDate);
            booked.append("start_time", formattedStartTime);
            booked.append("end_time", formattedEndTime);
            upcomingSessionList.add(booked);
        }
    }

    private int getTimeRange() {
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Time_Range");
            Document result = collection.find().first();

            if (result != null) {
                return result.getInteger("range");
            } else {
                LOGGER.warn("No document found in the 'Time_Range' collection.");
                return 30; // Default value
            }
        } else {
            LOGGER.error("Failed to connect to the database.");
            return 30; // Default value
        }
    }
}
