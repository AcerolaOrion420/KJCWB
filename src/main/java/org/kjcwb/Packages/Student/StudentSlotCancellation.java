package org.kjcwb.Packages.Student;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.kjcwb.Packages.Services.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class StudentSlotCancellation {
    private final MongoDatabase database;
    private static final Logger LOGGER = LoggerFactory.getLogger(StudentSlotCancellation.class);
    private final TimeUtility timeUtility = new TimeUtility();


    int slotStartMillis;
    int slotEndMillis;
    long dateMillis;
    String counsellorId;

    public StudentSlotCancellation() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            LOGGER.info("studentSlotCancellation class initialized and connected to DB");
        } else {
            LOGGER.error("studentSlotCancellation class failed to connect to the database");
        }
    }

    public void slotCancellation(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Invalid request body")));
            return;
        }
        String bookedSlotId = requestBody.getString("_id");
        String reason=requestBody.getString("reason");

        if (bookedSlotId == null) {
            ctx.response()
                    .putHeader("content-type", "text/plain")
                    .setStatusCode(400)
                    .end("bookedSlotId is required.");
            return;
        }
        boolean result=updateBooked_Slots(bookedSlotId,reason);
        boolean result1=false;
        if(result)
        {
            System.out.println("result "+result);
            String collection_name="Available_slots";
            result1=updateAppointmentTable(counsellorId,dateMillis,slotStartMillis,slotEndMillis,collection_name);
            collection_name="Counsellor_Leave_Calendar";
            updateAppointmentTable(counsellorId,dateMillis,slotStartMillis,slotEndMillis,collection_name);
            //counsellorLeaveCalendar(counsellorId,dateMillis,slotStartMillis,slotEndMillis);

        }
        if(result1 && result)
        {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Selected slot have been Cancelled!"));
        }
        else {
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new Document("error", "Internal Server Error")));
        }

    }


    private boolean updateBooked_Slots(String bookedSlotId,String reason) {
        boolean updated = false;
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Booked_slots");
            Document query = new Document("_id",bookedSlotId);
            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                while (cursor.hasNext()) {
                    Document document = cursor.next();
                    slotStartMillis = document.getInteger("slot_start_time_milliseconds");
                    slotEndMillis = document.getInteger("slot_end_time_milliseconds");
                    dateMillis=document.getLong("date_milliseconds");
                    counsellorId=document.getString("counsellor_id");

                    Document filter = new Document("_id", bookedSlotId);
                    Document updateDoc = new Document("$set", new Document("slot_status", "Cancelled")
                            .append("blocked_by", "Student")
                            .append("reason",reason));

                    UpdateResult updateResult = collection.updateOne(filter, updateDoc);

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
        return updated;
    }

    private boolean updateAppointmentTable(String counsellorId, long dateMilliseconds,int inputStartMillis,int inputEndMillis,String collection_name)
    {
        boolean updated=false;
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection(collection_name);
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
                                int slotStartMillis = slotDoc.getInteger("slot_start_time_milliseconds");
                                int slotEndMillis = slotDoc.getInteger("slot_end_time_milliseconds");
                                if (slotStartMillis == inputStartMillis && slotEndMillis == inputEndMillis) {
                                    slotDoc.put("status", false);

                                    Document filter = new Document("date_milliseconds", dateMilliseconds)
                                            .append("counsellors.counsellor_id", counsellorId)
                                            .append("counsellors.slots.slot_start_time_milliseconds", slotStartMillis)
                                            .append("counsellors.slots.slot_end_time_milliseconds", slotEndMillis);
                                    Document updateDoc = new Document("$set", new Document("counsellors.$[c].slots.$[s].status", false));

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
                                        updated=true;
                                        LOGGER.info("Document updated successfully updateAppointmentTable");
                                    } else {
                                        LOGGER.warn("Document not updated updateAppointmentTable");
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        return updated;
    }
}