package org.kjcwb.Packages.Counsellor;
import org.kjcwb.Packages.Services.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CounsellorBlockCalendar {

    private final MongoDatabase database;
    private static final Logger LOGGER = LoggerFactory.getLogger(CounsellorBlockCalendar.class);
    TimeUtility timeUtility = new TimeUtility();

    public CounsellorBlockCalendar() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            LOGGER.info("CounsellorBlockCalendar class initialized and connected to DB");
        } else {
            LOGGER.error("CounsellorBlockCalendar class failed to connect to the database");
        }
    }

    private List<Document> getDates(String counsellor_id, String date) {
        List<Document> slotsList = new ArrayList<>();
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Counsellor_Leave_Calendar");
            long inputDateMillis = timeUtility.dateToMilliseconds(date);

            Document dateRangeQuery = new Document("date_milliseconds", inputDateMillis);

            try (MongoCursor<Document> cursor = collection.find(dateRangeQuery).iterator()) {
                while (cursor.hasNext()) {
                    Document document = cursor.next();
                    // Retrieve the counselor array from the document
                    List<Document> counselors = (List<Document>) document.get("counsellors");
                    // Convert each counselor document to JsonObject format
                    for (Document counselorDoc : counselors) {
                        if (counsellor_id.equals(counselorDoc.getString("counsellor_id"))) {
                            List<Document> slots = (List<Document>) counselorDoc.get("slots");
                            if (slots != null) {
                                //slotsList.add((Document) slots);
                                for (Document slot : slots) {
                                    Document Doc = new Document()
                                            .append("slot_start_time_milliseconds", slot.getInteger("slot_start_time_milliseconds"))
                                            .append("slot_end_time_milliseconds", slot.getInteger("slot_end_time_milliseconds"))
                                            .append("blocked", slot.getBoolean("blocked"));
                                    slotsList.add(Doc);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error while fetching dates", e);
            }
        }
        return slotsList;
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
                            if(!slot.getBoolean("status"))
                            {
                                int slotStartMillis = slot.getInteger("slot_start_time_milliseconds");
                                long totalMillis = inputDateMillis + slotStartMillis;

                                if (totalMillis >= currentMillis) {
                                    JsonObject jsonDoc = new JsonObject()
                                            .put("start_time", timeUtility.timeFormatter(slot.getInteger("slot_start_time_milliseconds")))
                                            .put("end_time", timeUtility.timeFormatter(slot.getInteger("slot_end_time_milliseconds")));
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

    public void getAvailableSlots(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Invalid request body")));
            return;
        }
        String counsellorId = ctx.user().principal().getString("id");
        String date = requestBody.getString("date");

        List<Document> blocked = getDates(counsellorId, date);

        List<Document> availableSlots = new ArrayList<>();  // Initialize availableSlots list

        if (!blocked.isEmpty()) {
            for (Document slot : blocked) {
                if(!slot.getBoolean("blocked")) {
                    Document Doc = new Document()
                            .append("start_time", timeUtility.timeFormatter(slot.getInteger("slot_start_time_milliseconds")))
                            .append("end_time", timeUtility.timeFormatter(slot.getInteger("slot_end_time_milliseconds")));
                    availableSlots.add(Doc);
                }
            }
            if(availableSlots.isEmpty())
            {
                ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily("Entire Day has been blocked, cannot be modified contact admin"));
            }
            else {
                ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(Collections.singletonMap("slots", availableSlots)));
            }
        }
        else {
            List<JsonObject> primeSlotList=primeGetSlots(counsellorId,date);
            if(!primeSlotList.isEmpty())
            {
                ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(Collections.singletonMap("slots", primeSlotList)));
            }
            else {
                ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily("Slots cannot be blocked for the selected date,since the selected date is over, contact admin for further info."));
            }
        }
    }

    public void getBlockedSlots(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.singletonMap("error", "Invalid request body")));
            return;
        }
        String counsellorId = ctx.user().principal().getString("id");
        String date = requestBody.getString("date");

        List<Document> blocked = getDates(counsellorId, date);

        List<Document> availableSlots = new ArrayList<>();  // Initialize availableSlots list

        if (!blocked.isEmpty()) {
            for (Document slot : blocked) {
                if(slot.getBoolean("blocked")) {
                    Document Doc = new Document()
                            .append("start_time", timeUtility.timeFormatter(slot.getInteger("slot_start_time_milliseconds")))
                            .append("end_time", timeUtility.timeFormatter(slot.getInteger("slot_end_time_milliseconds")));
                    availableSlots.add(Doc);
                }
            }
            if(availableSlots.isEmpty())
            {
                ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily("No slots have been blocked"));
            }
            else {
                ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(Collections.singletonMap("slots", availableSlots)));
            }
        }
        else {
            ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("No slots have been blocked"));
        }
    }
}
