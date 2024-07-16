package org.kjcwb.Packages.Student;
import org.kjcwb.Packages.Services.*;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class SlotBooking {
    private final MongoDatabase database;
    private static final Logger LOGGER = LoggerFactory.getLogger(SlotBooking.class);
    private final TimeUtility timeUtility = new TimeUtility();
    String appointment_id=null;
    String slot_id=null;
    boolean data_updated=false;
    boolean counselorFoundGetDates = false;


    public SlotBooking() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            LOGGER.info("SlotBooking class initialized and connected to DB");
        } else {
            LOGGER.error("SlotBooking class failed to connect to the database");
        }
    }

    private long currentDateAndTime() {
        LocalDateTime now = LocalDateTime.now();
        return now.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private int convertTimeToMillis(String time) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime localTime = LocalTime.parse(time, formatter);
        return (int) ChronoUnit.MILLIS.between(LocalTime.MIDNIGHT, localTime);
    }

    private long dateToMilliseconds(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate formatdate = LocalDate.parse(date, formatter);
        return formatdate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public List<Document> checkSlotsInAppointment(long milliseconds) {
        List<Document> slotsList = new ArrayList<>();

        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Appointment");

            // Checking whether the document for the selected date is available in DB
            Document query = new Document("date_mils", milliseconds);

            MongoCursor<Document> cursor = collection.find(query).iterator();

            try {
                if (!cursor.hasNext()) {
                    slotsList = null;
                } else {
                    // We found the document for the selected date
                    while (cursor.hasNext()) {
                        Document document = cursor.next();
                        Document jsonObject = new Document()
                                .append("_id", document.getString("_id"))
                                .append("date", document.getString("date"))
                                .append("date_mils", document.getLong("date_mils"));

                        // Retrieve the counselor array from the document
                        List<Document> counselors = (List<Document>) document.get("counselor");
                        JsonArray counselorArray = new JsonArray();

                        // Convert each counselor document to JsonObject format
                        for (Document counselorDoc : counselors) {
                            JsonObject counselorObj = new JsonObject()
                                    .put("counselor_id", counselorDoc.getString("counselor_id"));

                            // Retrieve the slots array from the counselor document
                            List<Document> slots = (List<Document>) counselorDoc.get("slots");
                            JsonArray slotsArray = new JsonArray();

                            // Convert each slot document to JsonObject format
                            for (Document slotDoc : slots) {
                                Document slotObj = new Document()
                                        .append("slot_id", slotDoc.getString("slot_id"))
                                        .append("slot_s_time", slotDoc.getString("slot_s_time"))
                                        .append("slot_s_time_m", slotDoc.getInteger("slot_s_time_m"))
                                        .append("slot_e_time", slotDoc.getString("slot_e_time"))
                                        .append("slot_e_time_m", slotDoc.getInteger("slot_e_time_m"))
                                        .append("status", slotDoc.getBoolean("status"));
                                slotsArray.add(slotObj);
                            }
                            counselorObj.put("slots", slotsArray);
                            counselorArray.add(counselorObj);
                        }
                        jsonObject.put("counselor", counselorArray);
                        slotsList.add(jsonObject);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("An error occurred while querying the database: {}", e.getMessage());
            } finally {
                cursor.close();
            }
        } else {
            LOGGER.error("Failed to connect to the database.");
        }
        return slotsList;
    }

    private List<Document> primeGetSlots(String counsellor_id, long milliseconds,int slot_s_time_m) {

        List<Document> slotsList = new ArrayList<>();
        long currentmilliseconds = currentDateAndTime();
        long selectedmilliseconds=milliseconds+slot_s_time_m;
        if(selectedmilliseconds>=currentmilliseconds)
        {
            if (database != null) {
                //Get the collection named "Counsellor_Default_Slots"
                MongoCollection<Document> collection = database.getCollection("Counsellor_Default_Slots");

                //Query to find documents matching the counselor_id
                Document query = new Document("counselor_id", counsellor_id);
                MongoCursor<Document> cursor = collection.find(query).iterator();

                try {
                    if (cursor.hasNext()) {
                        Document doc = cursor.next();
                        List<Document> slots = (List<Document>) doc.get("slots");
                        if (slots != null) {
                            for (Document slot : slots) {
                                boolean status = slot.getBoolean("status");
                                if (!status) {
                                    int slot_s_time = slot.getInteger("slot_s_time_m");
                                    long totalmilliseconds = milliseconds + slot_s_time;

                                    if (totalmilliseconds >= currentmilliseconds) {
                                        Document Doc = new Document()
                                                .append("slot_id", slot.getString("slot_id"))
                                                .append("slot_s_time", slot.getString("slot_s_time"))
                                                .append("slot_s_time_m", slot.getInteger("slot_s_time_m"))
                                                .append("slot_e_time", slot.getString("slot_e_time"))
                                                .append("slot_e_time_m", slot.getInteger("slot_e_time_m"))
                                                .append("status", slot.getBoolean("status"));
                                        slotsList.add(Doc);
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            } else {
                LOGGER.error("Failed to connect to the database primeGetSlots.");
            }
        }
        else
        {
            LOGGER.error("selected a past date or time");
            slotsList=null;
        }
        return slotsList;
    }

    public static String generateUniqueId(String date, String suffix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate today = LocalDate.parse(date, formatter);
        String formattedDate = today.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        return suffix + formattedDate;
    }

    private void insertNewRecord(List<Document> slotsList, String cid, String date, long milliseconds, int slot_s_time_m, int slot_e_time_m) {
        MongoCollection<Document> collection = database.getCollection("Appointment");
        String uniqueId = generateUniqueId(date, "A");

        // Convert JsonObject list to Document list and update the status
        List<Document> slotDocuments = new ArrayList<>();
        for (Document slot : slotsList) {
            // Check if the slot matches the given start and end times
            int slotStartTime = slot.getInteger("slot_s_time_m");
            int slotEndTime = slot.getInteger("slot_e_time_m");
            if (slotStartTime == slot_s_time_m && slotEndTime == slot_e_time_m) {
                slot_id=slot.getString("slot_id");
                slot.put("status", true);
            }
            slotDocuments.add(slot);
        }
        Document counselor = new Document("counselor_id", cid)
                .append("slots", slotDocuments);
        Document newRecord = new Document("_id", uniqueId)
                .append("date", date)
                .append("date_mils", milliseconds)
                .append("counselor", Arrays.asList(counselor));


        collection.insertOne(newRecord);
        appointment_id=newRecord.getString("_id");
    }

    private List<Document> getDates(String counsellor_id, long milliseconds,int slot_s_time_m) {
        List<Document> dateList = new ArrayList<>();
        long currentmilliseconds = currentDateAndTime();
        long selectedmilliseconds=milliseconds+slot_s_time_m;
        if(selectedmilliseconds>=currentmilliseconds)
        {
            if (database != null) {
                MongoCollection<Document> collection = database.getCollection("Counsellor_Leave_Calendar");

                Document dateRangeQuery = new Document("date_milliseconds", milliseconds);

                try (MongoCursor<Document> cursor = collection.find(dateRangeQuery).iterator()) {
                    while (cursor.hasNext()) {
                        Document doc = cursor.next();
                        List<Document> counsellors = (List<Document>) doc.get("counsellors");

                        for (Document counsellor : counsellors) {
                            Long dMilliseconds = doc.getLong("date_milliseconds");

                            if (dMilliseconds != null && dMilliseconds.equals(milliseconds) && counsellor.getString("counsellor_id").equals(counsellor_id) && counsellor.getBoolean("inactive")) {
                                counselorFoundGetDates = true;
                                List<Document> slots = (List<Document>) counsellor.get("slots");
                                if (slots != null) {
                                    for (Document slot : slots) {
                                        boolean status = slot.getBoolean("status");
                                        if (!status) {
                                            int slot_s_time = slot.getInteger("slot_s_time_m");
                                            long totalmilliseconds = milliseconds + slot_s_time;

                                            if (totalmilliseconds >= currentmilliseconds) {
                                                Document Doc = new Document()
                                                        .append("slot_id", slot.getString("slot_id"))
                                                        .append("slot_s_time", slot.getString("slot_s_time"))
                                                        .append("slot_s_time_m", slot.getInteger("slot_s_time_m"))
                                                        .append("slot_e_time", slot.getString("slot_e_time"))
                                                        .append("slot_e_time_m", slot.getInteger("slot_e_time_m"))
                                                        .append("status", slot.getBoolean("status"));
                                                dateList.add(Doc);
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error while fetching dates", e);
                }

            } else {
                LOGGER.error("Failed to connect to the database in getDates().");
            }
        }
        return dateList;
    }

    public void getSlots(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.emptyList()));
            return;
        }

        String counsellor_id = requestBody.getString("counsellor_id");
        String date = requestBody.getString("date");
        String slot_s_time = requestBody.getString("slot_start_time");
        String slot_e_time = requestBody.getString("slot_end_time");
        String ageStr = requestBody.getString("age");
        int age = ageStr != null ? Integer.parseInt(ageStr) : -1;
        String referred_by = requestBody.getString("referred_by");
        String referrer_email = requestBody.getString("referrer_email");

        long booked_on = currentDateAndTime();

        if (counsellor_id == null || date == null || slot_s_time == null || slot_e_time == null || ageStr == null || referred_by == null || referrer_email == null) {
            ctx.response()
                    .putHeader("content-type", "text/plain")
                    .setStatusCode(400)
                    .end("Counsellor ID and Date are required.");
            return;
        }
        int slot_s_time_m = timeUtility.timeToMilliseconds(slot_s_time);
        int slot_e_time_m = timeUtility.timeToMilliseconds(slot_e_time);
        long milliseconds = dateToMilliseconds(date);

        List<Document> slotsList = checkSlotsInAppointment(milliseconds);

        if (slotsList == null || slotsList.isEmpty()) {
            List<Document> dateList  = getDates(counsellor_id, milliseconds, slot_s_time_m);
            if(!dateList.isEmpty() && counselorFoundGetDates)
            {
                counselorFoundGetDates=false;
                insertNewRecord(dateList, counsellor_id, date, milliseconds, slot_s_time_m, slot_e_time_m);
                updateLeaveCalendar( counsellor_id,  milliseconds,  slot_s_time_m,  slot_e_time_m);
                data_updated = true;
            }
            else if(counselorFoundGetDates && dateList.isEmpty()) {
                counselorFoundGetDates=false;
                ctx.response().putHeader("content-type", "application/json")
                        .end(Json.encodePrettily("Slots are not available for the day"));
                return;
            }
            else {
                slotsList = primeGetSlots(counsellor_id, milliseconds, slot_s_time_m);
                if (slotsList != null) {
                    insertNewRecord(slotsList, counsellor_id, date, milliseconds, slot_s_time_m, slot_e_time_m);
                    data_updated = true;
                } else {
                    LOGGER.error("User gave a past time or past date as input");
                }
            }


        } else {
            for (Document slot : slotsList) {

                appointment_id = slot.getString("_id");
                JsonArray counselorsArray = slot.get("counselor", JsonArray.class);
                boolean counselorFound=false;
                if (counselorsArray != null) {

                    for (int i = 0; i < counselorsArray.size(); i++) {
                        JsonObject counselor = counselorsArray.getJsonObject(i);

                        if (counselor.getString("counselor_id").equals(counsellor_id)) {
                            counselorFound = true;
                            JsonArray slotsArray = counselor.getJsonArray("slots");
                            for (int j = 0; j < slotsArray.size(); j++) {
                                JsonObject slotObj = slotsArray.getJsonObject(j);
                                int slotStartTime = slotObj.getInteger("slot_s_time_m");
                                int slotEndTime = slotObj.getInteger("slot_e_time_m");
                                if (slotStartTime == slot_s_time_m && slotEndTime == slot_e_time_m) {
                                    slot_id = slotObj.getString("slot_id");
                                    slotObj.put("status", true);

                                    MongoCollection<Document> collection = database.getCollection("Appointment");
                                    Document filter = new Document("_id", slot.getString("_id"))
                                            .append("counselor.counselor_id", counsellor_id)
                                            .append("counselor.slots.slot_s_time_m", slot_s_time_m)
                                            .append("counselor.slots.slot_e_time_m", slot_e_time_m);
                                    Document updateDoc = new Document("$set", new Document("counselor.$[c].slots.$[s].status", true));

                                    UpdateOptions options = new UpdateOptions();
                                    options.arrayFilters(List.of(
                                            Filters.eq("c.counselor_id", counsellor_id),
                                            Filters.and(
                                                    Filters.eq("s.slot_s_time_m", slot_s_time_m),
                                                    Filters.eq("s.slot_e_time_m", slot_e_time_m)
                                            )
                                    ));
                                    UpdateResult updateResult = collection.updateOne(
                                            filter,
                                            updateDoc,
                                            options
                                    );
                                    if (updateResult.getModifiedCount() > 0) {
                                        LOGGER.info("Document updated successfully getSlots");
                                        data_updated = true;
                                    } else {
                                        LOGGER.error("Document not updated getSlots ");
                                    }
                                    break;
                                }
                            }
                            break; // Exit loop after updating the first matching counselor
                        }
                    }

                    if (!counselorFound) {
                        List<Document> getDateSlots = getDates(counsellor_id, milliseconds, slot_s_time_m);

                        if (!getDateSlots.isEmpty() &&  counselorFoundGetDates)
                        {
                            counselorFoundGetDates=false;
                            List<Document> getDatDocuments = new ArrayList<>();
                            for (Document slot2 : getDateSlots) {
                                // Check if the slot matches the given start and end times
                                int slotStartTime = slot2.getInteger("slot_s_time_m");

                                int slotEndTime = slot2.getInteger("slot_e_time_m");

                                if (slotStartTime == slot_s_time_m && slotEndTime == slot_e_time_m) {
                                    slot_id = slot2.getString("slot_id");
                                    slot2.put("status", true);
                                }
                                getDatDocuments.add(slot2);
                            }

                            // Check if primeGetSlots returned valid slots
                            if (!getDateSlots.isEmpty()) {
                                // Retrieve counselorsList from the current slot document
                                JsonArray counselorsList = slot.get("counselor", JsonArray.class);

                                if (counselorsList != null) {
                                    // Create new counselor document to append
                                    Document newCounselor = new Document();
                                    newCounselor.put("counselor_id", counsellor_id);
                                    newCounselor.put("slots", getDatDocuments);

                                    // Append new counselor to the existing counselor array
                                    counselorsList.add(newCounselor);

                                    List<Document> counselorDocuments = new ArrayList<>();

                                    for (Object obj : counselorsList) {
                                        if (obj instanceof JsonObject jsonObject) {
                                            Document counselorDocument = Document.parse(jsonObject.toString());
                                            counselorDocuments.add(counselorDocument);
                                        }
                                    }

                                    // Update the document in the database
                                    MongoCollection<Document> collection = database.getCollection("Appointment");

                                    // Filter to find the document to update using its _id
                                    Document filter = new Document("_id", slot.getString("_id"));

                                    // Create update operation
                                    Document updateDoc = new Document("$set", new Document("counselor", counselorDocuments));

                                    // Perform update operation
                                    UpdateResult updateResult = collection.updateOne(filter, updateDoc);

                                    // Check if the update was successful
                                    if (updateResult.getModifiedCount() > 0) {
                                        data_updated = true;
                                        updateLeaveCalendar( counsellor_id,  milliseconds,  slot_s_time_m,  slot_e_time_m);
                                        LOGGER.info("Document updated successfully with new counselor and slots getSlots");
                                    } else {
                                        LOGGER.error("Document not updated getSlots");
                                    }
                                    break;  // Exit loop after updating the first matching slot
                                }
                            } else {
                                LOGGER.error("No valid slots returned from getslots or input is in the past");
                            }

                        }
                        else if(counselorFoundGetDates && getDateSlots.isEmpty()) {
                            counselorFoundGetDates=false;
                            ctx.response().putHeader("content-type", "application/json")
                                    .end(Json.encodePrettily("Slots are not available for the day"));
                            return;
                        }
                        else {
                            // Retrieve slots from primeGetSlots function
                            List<Document> primeslotsList = primeGetSlots(counsellor_id, milliseconds, slot_s_time_m);

                            List<Document> slotDocuments = new ArrayList<>();
                            for (Document slot2 : primeslotsList) {
                                // Check if the slot matches the given start and end times
                                int slotStartTime = slot2.getInteger("slot_s_time_m");

                                int slotEndTime = slot2.getInteger("slot_e_time_m");

                                if (slotStartTime == slot_s_time_m && slotEndTime == slot_e_time_m) {
                                    slot_id = slot2.getString("slot_id");
                                    slot2.put("status", true);
                                }
                                slotDocuments.add(slot2);
                            }

                            // Check if primeGetSlots returned valid slots
                            if (!primeslotsList.isEmpty()) {
                                // Retrieve counselorsList from the current slot document
                                JsonArray counselorsList = slot.get("counselor", JsonArray.class);

                                if (counselorsList != null) {
                                    // Create new counselor document to append
                                    Document newCounselor = new Document();
                                    newCounselor.put("counselor_id", counsellor_id);
                                    newCounselor.put("slots", slotDocuments);

                                    // Append new counselor to the existing counselor array
                                    counselorsList.add(newCounselor);

                                    List<Document> counselorDocuments = new ArrayList<>();

                                    for (Object obj : counselorsList) {
                                        if (obj instanceof JsonObject jsonObject) {
                                            Document counselorDocument = Document.parse(jsonObject.toString());
                                            counselorDocuments.add(counselorDocument);
                                        }
                                    }

                                    // Update the document in the database
                                    MongoCollection<Document> collection = database.getCollection("Appointment");

                                    // Filter to find the document to update using its _id
                                    Document filter = new Document("_id", slot.getString("_id"));

                                    // Create update operation
                                    Document updateDoc = new Document("$set", new Document("counselor", counselorDocuments));

                                    // Perform update operation
                                    UpdateResult updateResult = collection.updateOne(filter, updateDoc);

                                    // Check if the update was successful
                                    if (updateResult.getModifiedCount() > 0) {
                                        data_updated = true;
                                        LOGGER.info("Document updated successfully with new counselor and slots");
                                    } else {
                                        LOGGER.error("Document not updated");
                                    }
                                    break;  // Exit loop after updating the first matching slot
                                }
                            } else {
                                LOGGER.error("No valid slots returned from primeGetSlots or input is in the past");
                            }
                        }
                    }
                }
            }
        }
        InsertOneResult result = null;
        if (data_updated) {
            String student_id = ctx.user().principal().getString("id");
            String Booked_appointment_id = generateUniqueId(date, "BS") + student_id;
            Document appointment_booked = new Document("_id", Booked_appointment_id)
                    .append("appointment_id", appointment_id)
                    .append("date", date)
                    .append("date_mils", milliseconds)
                    .append("booked_on_date_milliseconds", booked_on)
                    .append("counselor_id", counsellor_id)
                    .append("student_id", student_id)
                    .append("slot_id", slot_id)
                    .append("slot_s_time_m", slot_s_time_m)
                    .append("slot_e_time_m", slot_e_time_m)
                    .append("referred_by", referred_by)
                    .append("referrer_Email", referrer_email)
                    .append("student_age", age)
                    .append("slot_status", "pending");

            MongoCollection<Document> collection = database.getCollection("Booked_Slots");
            result = collection.insertOne(appointment_booked);
        } else {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Failed to book slot! try again."));
        }

        assert result != null;
        if (result.wasAcknowledged()) {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Your slot has been booked!"));
        } else {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Failed to book slot! try again or contact Admin."));
        }
    }

    private void updateLeaveCalendar(String counsellor_id, long milliseconds, int slot_s_time_m, int slot_e_time_m) {
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Counsellor_Leave_Calendar");

            Bson filter = Filters.and(
                    Filters.eq("date_milliseconds", milliseconds),
                    Filters.eq("counsellors.counsellor_id", counsellor_id),
                    Filters.eq("counsellors.slots.slot_s_time_m", slot_s_time_m),
                    Filters.eq("counsellors.slots.slot_e_time_m", slot_e_time_m)
            );

            Document updateDoc = new Document("$set", new Document("counsellors.$[c].slots.$[s].status", true));

            UpdateOptions options = new UpdateOptions().arrayFilters(List.of(
                    Filters.eq("c.counsellor_id", counsellor_id),
                    Filters.and(
                            Filters.eq("s.slot_s_time_m", slot_s_time_m),
                            Filters.eq("s.slot_e_time_m", slot_e_time_m)
                    )
            ));

            try {
                UpdateResult updateResult = collection.updateOne(filter, updateDoc, options);

                if (updateResult.getModifiedCount() > 0) {
                    LOGGER.info("Document updated successfully");
                } else {
                    LOGGER.warn("Document not updated or no matching document found");
                }
            } catch (Exception e) {
                LOGGER.error("Error while updating leave calendar", e);
            }
        } else {
            LOGGER.error("Failed to connect to the database in updateLeaveCalendar().");
        }
    }
}