package org.kjcwb.Packages.Counsellor;
import org.kjcwb.Packages.Services.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CounsellorBlockCalendarSubmit {
    private final MongoDatabase database;
    private static final Logger LOGGER = LoggerFactory.getLogger(CounsellorBlockCalendarSubmit.class);
    private final TimeUtility timeUtility = new TimeUtility();

    List<Document> studentIds = new ArrayList<>();


    public CounsellorBlockCalendarSubmit() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            LOGGER.info("BlockCalendarSubmit class initialized and connected to DB");
        } else {
            LOGGER.error("BlockCalendarSubmit class failed to connect to the database");
        }
    }

    public void blockSlots(RoutingContext ctx) {

        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Invalid request body")));
            return;
        }

        String counsellorId = ctx.user().principal().getString("id");
        String date = requestBody.getString("date");
        JsonArray slots = requestBody.getJsonArray("slots");
        String reason = requestBody.getString("reason");

        boolean result1 = false;
        long dateMillis = timeUtility.dateToMilliseconds(date);

        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Counsellor_Leave_Calendar");
            Document query = new Document("date_milliseconds", dateMillis);

            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                if (cursor.hasNext()) {
                    Document document = cursor.next();
                    List<Document> counselors = (List<Document>) document.get("counsellors");
                    boolean counselorFound = false;
                    for (Document counselorDoc : counselors) {
                        String dbCounselorId = counselorDoc.getString("counsellor_id");
                        if (dbCounselorId != null && dbCounselorId.equals(counsellorId)) {
                            List<Document> slotList = (List<Document>) counselorDoc.get("slots");
                            for (Document slotDoc : slotList) {
                                long slotStartMillis = slotDoc.getInteger("slot_start_time_milliseconds");
                                long slotEndMillis = slotDoc.getInteger("slot_end_time_milliseconds");

                                for (int i = 0; i < slots.size(); i++) {
                                    JsonObject slot = slots.getJsonObject(i);
                                    long inputStartMillis = timeUtility.timeToMilliseconds(slot.getString("start_time"));
                                    long inputEndMillis = timeUtility.timeToMilliseconds(slot.getString("end_time"));

                                    if (slotStartMillis == inputStartMillis && slotEndMillis == inputEndMillis) {
                                        slotDoc.put("blocked", true);
                                        slotDoc.put("reason", reason);
                                        slotDoc.put("Blocked_By","Counsellor");

                                        Document filter = new Document("date_milliseconds", dateMillis)
                                                .append("counsellors.counsellor_id", counsellorId)
                                                .append("counsellors.slots.slot_start_time_milliseconds", slotStartMillis)
                                                .append("counsellors.slots.slot_end_time_milliseconds", slotEndMillis);
                                        Document updateDoc = new Document("$set", new Document("counsellors.$[c].slots.$[s].blocked", true)
                                                .append("counsellors.$[c].slots.$[s].reason", reason)
                                                .append("counsellors.$[c].slots.$[s].Blocked_By","Counsellor"));

                                        UpdateOptions options = new UpdateOptions().arrayFilters(List.of(
                                                Filters.eq("c.counsellor_id", counsellorId),
                                                Filters.and(
                                                        Filters.eq("s.slot_start_time_milliseconds", slotStartMillis),
                                                        Filters.eq("s.slot_end_time_milliseconds", slotEndMillis)
                                                )
                                        ));
                                        UpdateResult updateResult = collection.updateOne(
                                                filter,
                                                updateDoc,
                                                options
                                        );

                                        if (updateResult.getModifiedCount() > 0) {
                                            result1 = true;
                                            LOGGER.info("Document updated successfully");
                                        } else {
                                            LOGGER.warn("Document not updated");
                                        }
                                    }
                                }
                            }
                            counselorFound = true;
                            break;
                        }
                    }

                    if (!counselorFound) {
                        List<JsonObject> primeSlotList = primeGetSlots(counsellorId, date);
                        List<Document> newSlots = new ArrayList<>();

                        for (JsonObject primeSlot : primeSlotList) {
                            long slotStartTime = primeSlot.getInteger("slot_start_time_milliseconds");
                            long slotEndTime = primeSlot.getInteger("slot_end_time_milliseconds");

                            for (int i = 0; i < slots.size(); i++) {
                                JsonObject inputSlot = slots.getJsonObject(i);
                                long inputStartMillis = timeUtility.timeToMilliseconds(inputSlot.getString("start_time"));
                                long inputEndMillis = timeUtility.timeToMilliseconds(inputSlot.getString("end_time"));
                                if (inputStartMillis == slotStartTime && inputEndMillis == slotEndTime) {
                                    primeSlot.put("blocked", true);
                                    primeSlot.put("reason", reason);
                                    primeSlot.put("Blocked_By","Counsellor");
                                }
                            }
                            newSlots.add(new Document(primeSlot.getMap()));
                        }

                        Document newCounselorDoc = new Document("counsellor_id", counsellorId)
                                .append("Blocked By", "counsellor")
                                .append("slots", newSlots);

                        UpdateResult updateResult = collection.updateOne(
                                new Document("date_milliseconds", dateMillis),
                                new Document("$push", new Document("counsellors", newCounselorDoc))
                        );
                        if (updateResult.getModifiedCount() > 0) {
                            result1 = true;
                            LOGGER.info("Document updated successfully blockCalendar");
                        } else {
                            LOGGER.warn("Document not updated blockCalendar");
                        }
                    }
                } else {
                    LOGGER.warn("No document found for the given date blockCalendar");
                    List<JsonObject> primeSlotList = primeGetSlots(counsellorId, date);
                    List<Document> newSlots = new ArrayList<>();

                    for (JsonObject primeSlot : primeSlotList) {
                        long slotStartTime = primeSlot.getInteger("slot_start_time_milliseconds");
                        long slotEndTime = primeSlot.getInteger("slot_end_time_milliseconds");

                        //boolean slotMatched = false;
                        for (int i = 0; i < slots.size(); i++) {
                            JsonObject inputSlot = slots.getJsonObject(i);
                            long inputStartMillis = timeUtility.timeToMilliseconds(inputSlot.getString("start_time"));
                            long inputEndMillis = timeUtility.timeToMilliseconds(inputSlot.getString("end_time"));
                            if (inputStartMillis == slotStartTime && inputEndMillis == slotEndTime) {
                                primeSlot.put("blocked", true);
                                primeSlot.put("reason", reason);
                                primeSlot.put("Blocked_By","Counsellor");
                            }
                        }
                        newSlots.add(new Document(primeSlot.getMap()));
                    }

                    Document newCounselorDoc = new Document("counsellor_id", counsellorId)
                            .append("slots", newSlots);

                    Document newDateDoc = new Document("_id", generateUniqueId(date, "BD"))
                            .append("date", date)
                            .append("date_milliseconds", dateMillis)
                            .append("counsellors", List.of(newCounselorDoc));

                    InsertOneResult updateResult = collection.insertOne(newDateDoc);

                    if (updateResult.getInsertedId() != null) {
                        result1 = true;
                        LOGGER.info("Document updated successfully blockCalendar 2");
                    } else {
                        LOGGER.warn("Document not updated blockCalendar 2");
                    }
                }
            }
        }
        boolean result2;
        if (result1) {
            result2 = updateBooked_Slots(counsellorId, dateMillis, slots, reason);
            updateAppointmentTable(counsellorId, dateMillis, slots,true);
            if (result2) {
                Vertx vertx = ctx.vertx();
                sendEmail(studentIds, vertx);
            }
        }
        if (result1) {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Selected slots have been blocked!"));
        } else {
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new Document("error", "Internal Server Error")));
        }
    }


    public void unblockSlots(RoutingContext ctx) {

        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Invalid request body")));
            return;
        }

        String counsellorId = ctx.user().principal().getString("id");
        String date = requestBody.getString("date");
        JsonArray slots = requestBody.getJsonArray("slots");

        boolean result1 = false;
        long dateMillis = timeUtility.dateToMilliseconds(date);

        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Counsellor_Leave_Calendar");
            Document query = new Document("date_milliseconds", dateMillis)
                    .append("counsellors.counsellor_id", counsellorId);
            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                if (cursor.hasNext()) {
                    Document document = cursor.next();
                    List<Document> counselors = (List<Document>) document.get("counsellors");
                    boolean counselorFound = false;
                    for (Document counselorDoc : counselors) {
                        String dbCounselorId = counselorDoc.getString("counsellor_id");
                        if (dbCounselorId != null && dbCounselorId.equals(counsellorId)) {
                            List<Document> slotList = (List<Document>) counselorDoc.get("slots");
                            for (Document slotDoc : slotList) {
                                long slotStartMillis = slotDoc.getInteger("slot_start_time_milliseconds");
                                long slotEndMillis = slotDoc.getInteger("slot_end_time_milliseconds");

                                for (int i = 0; i < slots.size(); i++) {
                                    JsonObject slot = slots.getJsonObject(i);
                                    long inputStartMillis = timeUtility.timeToMilliseconds(slot.getString("start_time"));
                                    long inputEndMillis = timeUtility.timeToMilliseconds(slot.getString("end_time"));

                                    if (slotStartMillis == inputStartMillis && slotEndMillis == inputEndMillis) {
                                        slotDoc.put("blocked", false);

                                        Document filter = new Document("date_milliseconds", dateMillis)
                                                .append("counsellors.counsellor_id", counsellorId)
                                                .append("counsellors.slots.slot_start_time_milliseconds", slotStartMillis)
                                                .append("counsellors.slots.slot_end_time_milliseconds", slotEndMillis);
                                        Document updateDoc = new Document("$set", new Document("counsellors.$[c].slots.$[s].blocked", false))
                                                .append("$unset", new Document("counsellors.$[c].slots.$[s].reason", "")
                                                        .append("counsellors.$[c].slots.$[s].Blocked_By", ""));

                                        UpdateOptions options = new UpdateOptions().arrayFilters(List.of(
                                                Filters.eq("c.counsellor_id", counsellorId),
                                                Filters.and(
                                                        Filters.eq("s.slot_start_time_milliseconds", slotStartMillis),
                                                        Filters.eq("s.slot_end_time_milliseconds", slotEndMillis)
                                                )
                                        ));
                                        UpdateResult updateResult = collection.updateOne(
                                                filter,
                                                updateDoc,
                                                options
                                        );

                                        if (updateResult.getModifiedCount() > 0) {
                                            result1 = true;
                                            updateAppointmentTable(counsellorId, dateMillis, slots,false);
                                            LOGGER.info("Document updated successfully");
                                        } else {
                                            LOGGER.warn("Document not updated");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (result1) {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Selected slots have been unblocked and they are available for booking"));
        } else {
            ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Internal Server Error"));
        }

    }


    private boolean updateBooked_Slots(String counsellorId, long dateMilliseconds, JsonArray slots, String reason) {
        boolean updated = false;
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Booked_slots");
            Document query = new Document("date_milliseconds", dateMilliseconds)
                    .append("counsellor_id", counsellorId)
                    .append("slot_status", "pending");

            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                while (cursor.hasNext()) {
                    Document document = cursor.next();
                    long slotStartMillis = document.getInteger("slot_start_time_milliseconds");
                    long slotEndMillis = document.getInteger("slot_end_time_milliseconds");

                    for (int i = 0; i < slots.size(); i++) {
                        JsonObject slot = slots.getJsonObject(i);
                        long inputStartMillis = timeUtility.timeToMilliseconds(slot.getString("start_time"));
                        long inputEndMillis = timeUtility.timeToMilliseconds(slot.getString("end_time"));

                        if (slotStartMillis == inputStartMillis && slotEndMillis == inputEndMillis) {
                            Document filter = new Document("date_milliseconds", dateMilliseconds)
                                    .append("counsellor_id", counsellorId)
                                    .append("slot_start_time_milliseconds", slotStartMillis)
                                    .append("slot_end_time_milliseconds", slotEndMillis);
                            Document updateDoc = new Document("$set", new Document("slot_status", "Cancelled")
                                    .append("blocked_by", "Counsellor")
                                    .append("reason", reason));

                            UpdateResult updateResult = collection.updateOne(filter, updateDoc);
                            Document studentdata = new Document();
                            studentdata.append("Student_id", document.getString("student_id"));
                            studentdata.append("Booked Slot Date ", TimeUtility.millisecondsToDate(document.getLong("date_milliseconds")));
                            studentdata.append("Booked Slot Time", timeUtility.timeFormatter(document.getInteger("slot_start_time_milliseconds")) + " to " + timeUtility.timeFormatter(document.getInteger("slot_end_time_milliseconds")));
                            studentIds.add(studentdata);
                            if (updateResult.getModifiedCount() > 0) {
                                updated = true;
                                LOGGER.info("Document updated successfully for student ID: " + document.getString("student_id"));
                            } else {
                                LOGGER.warn("Document not updated for student ID: " + document.getString("student_id"));
                            }
                            break; // Break the loop once the document is updated
                        }
                    }
                }
            }
        }
        return updated;
    }

    private void sendEmail(List<Document> studentIds, Vertx vertx) {
        if (database != null) {
            for (Document doc : studentIds) {
                MongoCollection<Document> collection = database.getCollection("Student");

                String studentId = doc.getString("Student_id");
                String bookedSlotDate = doc.getString("Booked Slot Date ");
                String bookedSlotTime = doc.getString("Booked Slot Time");

                if (studentId != null) {
                    Document query = new Document("_id", studentId);
                    try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                        if (!cursor.hasNext()) {
                            LOGGER.warn("No student found with ID: " + studentId);
                            continue;
                        }
                        Document studentDoc = cursor.next();
                        String email = studentDoc.getString("email");
                        if (email == null || email.isEmpty()) {
                            LOGGER.warn("No email found for student ID: " + studentId);
                            continue;
                        }
                        String subject = "Counselling session has been cancelled";
                        String text = "Dear Student,\n\nYour counselling session booked on "
                                + bookedSlotDate + " from "
                                + bookedSlotTime
                                + " has been cancelled.\n\nRegards,\nCounselling Team";
                        EmailService.sendEmailAsync(vertx, email, subject, text);
                    }
                } else {
                    LOGGER.error("Student ID is null for the provided document.");
                }
            }
        } else {
            LOGGER.error("Database is not initialized.");
        }
    }


    private void updateAppointmentTable(String counsellorId, long dateMilliseconds, JsonArray slots, boolean flag) {
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Available_slots");
            Document query = new Document("date_milliseconds", dateMilliseconds);

            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                if (cursor.hasNext()) {
                    Document document = cursor.next();
                    List<Document> counselors = (List<Document>) document.get("counsellors");

                    for (Document counselorDoc : counselors) {
                        String dbCounselorId = counselorDoc.getString("counsellor_id");
                        if (dbCounselorId != null && dbCounselorId.equals(counsellorId)) {
                            List<Document> slotList = (List<Document>) counselorDoc.get("slots");
                            for (Document slotDoc : slotList) {
                                long slotStartMillis = slotDoc.getInteger("slot_start_time_milliseconds");
                                long slotEndMillis = slotDoc.getInteger("slot_end_time_milliseconds");

                                for (int i = 0; i < slots.size(); i++) {
                                    JsonObject slot = slots.getJsonObject(i);
                                    long inputStartMillis = timeUtility.timeToMilliseconds(slot.getString("start_time"));
                                    long inputEndMillis = timeUtility.timeToMilliseconds(slot.getString("end_time"));

                                    if (slotStartMillis == inputStartMillis && slotEndMillis == inputEndMillis) {
                                        slotDoc.put("status", flag);

                                        Document filter = new Document("date_milliseconds", dateMilliseconds)
                                                .append("counsellors.counsellor_id", counsellorId)
                                                .append("counsellors.slots.slot_start_time_milliseconds", slotStartMillis)
                                                .append("counsellors.slots.slot_end_time_milliseconds", slotEndMillis);
                                        Document updateDoc = new Document("$set", new Document("counsellors.$[c].slots.$[s].status", flag));

                                        UpdateOptions options = new UpdateOptions().arrayFilters(List.of(
                                                Filters.eq("c.counsellor_id", counsellorId),
                                                Filters.and(
                                                        Filters.eq("s.slot_start_time_milliseconds", slotStartMillis),
                                                        Filters.eq("s.slot_end_time_milliseconds", slotEndMillis)
                                                )
                                        ));
                                        UpdateResult updateResult = collection.updateOne(
                                                filter,
                                                updateDoc,
                                                options
                                        );

                                        if (updateResult.getModifiedCount() > 0) {
                                            LOGGER.info("Document updated successfully updateAppointmentTable");
                                        } else {
                                            LOGGER.warn("Document not updated updateAppointmentTable");
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private String generateUniqueId(String date, String suffix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate today = LocalDate.parse(date, formatter);
        String formattedDate = today.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        return suffix + formattedDate;
    }

    private List<JsonObject> primeGetSlots(String cid, String date) {
        List<JsonObject> slotsList = new ArrayList<>();
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Counsellor_Default_Slots");
            Document query = new Document("counsellor_id", cid);

            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                if (cursor.hasNext()) {
                    Document doc = cursor.next();
                    long inputDateMillis = timeUtility.dateToMilliseconds(date);
                    long currentMillis = timeUtility.currentDateAndTimeMillis();

                    List<Document> slots = (List<Document>) doc.get("slots");
                    if (slots != null) {
                        for (Document slot : slots) {
                            boolean status = slot.getBoolean("status");
                            if (!status) {
                                int slotStartMillis = slot.getInteger("slot_start_time_milliseconds");
                                long totalMillis = inputDateMillis + slotStartMillis;

                                if (totalMillis >= currentMillis) {
                                    JsonObject jsonDoc = new JsonObject()
                                            .put("slot_id", slot.getString("slot_id"))
                                            .put("slot_s_time", slot.getString("slot_s_time"))
                                            .put("slot_start_time_milliseconds", slot.getInteger("slot_start_time_milliseconds"))
                                            .put("slot_e_time", slot.getString("slot_e_time"))
                                            .put("slot_end_time_milliseconds", slot.getInteger("slot_end_time_milliseconds"))
                                            .put("status", slot.getBoolean("status"))
                                            .put("blocked", false);
                                    slotsList.add(jsonDoc);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error while fetching prime slots", e);
            }
        } else {
            LOGGER.error("Failed to connect to the database.");
        }
        return slotsList;
    }
}