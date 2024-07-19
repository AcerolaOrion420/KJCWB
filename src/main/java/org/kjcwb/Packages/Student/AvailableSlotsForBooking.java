package org.kjcwb.Packages.Student;
import org.kjcwb.Packages.Services.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;


public class AvailableSlotsForBooking {
    private final MongoDatabase database;
    private static final Logger LOGGER = LoggerFactory.getLogger(AvailableSlotsForBooking.class);
    private final TimeUtility timeUtility = new TimeUtility();
    private boolean counsellorFoundGetDates = false;

    public AvailableSlotsForBooking() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            LOGGER.info("Slots class initialized and connected to DB");
        } else {
            LOGGER.error("Slots class failed to connect to the database");
        }
    }

    private List<JsonObject> getDates(String counsellorId, String date) {
        List<JsonObject> dateList = new ArrayList<>();
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Counsellor_Leave_Calendar");
            long inputDateMillis = timeUtility.dateToMilliseconds(date);
            int range = getRange();

            if (range <= 0) {
                LOGGER.error("Date frame for booking cannot be less than or equal to 0, please check the Date_Range collection");
            } else {
                Document dateRangeQuery = new Document("date_milliseconds", inputDateMillis);

                try (MongoCursor<Document> cursor = collection.find(dateRangeQuery).iterator()) {
                    while (cursor.hasNext()) {
                        Document doc = cursor.next();
                        List<Document> counsellors = (List<Document>) doc.get("counsellors");

                        for (Document counsellor : counsellors) {
                            Long dMilliseconds = doc.getLong("date_milliseconds");

                            if (dMilliseconds != null && dMilliseconds.equals(inputDateMillis)
                                    && counsellor.getString("counsellor_id").equals(counsellorId)) {
                                counsellorFoundGetDates = true;

                                List<Document> slots = (List<Document>) counsellor.get("slots");
                                if (slots != null) {
                                    for (Document slot : slots) {
                                        if (!slot.getBoolean("status") && !slot.getBoolean("blocked")) {
                                            String formattedStartTime = timeUtility.timeFormatter(slot.getInteger("slot_start_time_milliseconds"));
                                            String formattedEndTime = timeUtility.timeFormatter(slot.getInteger("slot_end_time_milliseconds"));
                                            JsonObject slotJson = new JsonObject()
                                                    .put("start_time", formattedStartTime)
                                                    .put("end_time", formattedEndTime);
                                            dateList.add(slotJson);
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
            }
        } else {
            LOGGER.error("Failed to connect to the database in getDates().");
        }
        return dateList;
    }



    private int getRange() {
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Date_Range");
            Document result = collection.find().first();

            if (result != null) {
                return result.getInteger("range");
            } else {
                LOGGER.error("No document found in the 'Range' collection.");
                return -1;
            }
        } else {
            LOGGER.error("Failed to connect to the database in getRange().");
            return -1;
        }
    }

    //Method which gets the Counsellor default slots
    private List<JsonObject> primeGetSlots(String counsellor_id, String date) {
        List<JsonObject> slotsList = new ArrayList<>();
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Counsellor_Default_Slots");
            Document query = new Document("counsellor_id", counsellor_id);
System.out.println("counsellor_id "+counsellor_id);
            System.out.println("date "+date);
            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                if (cursor.hasNext()) {
                    Document doc = cursor.next();
                    long inputDateMillis = timeUtility.dateToMilliseconds(date);
                    long currentMillis = timeUtility.currentDateAndTimeMillis();
                    System.out.println("currentMillis "+currentMillis);
                    List<Document> slots = (List<Document>) doc.get("slots");
                    if (slots != null) {
                        for (Document slot : slots) {
                            boolean status = slot.getBoolean("status");
                            if (!status) {
                                int slotStartMillis = slot.getInteger("slot_start_time_milliseconds");
                                long totalMillis = inputDateMillis + slotStartMillis;

                                String formattedStartTime = timeUtility.timeFormatter(slot.getInteger("slot_start_time_milliseconds"));
                                String formattedEndTime = timeUtility.timeFormatter(slot.getInteger("slot_end_time_milliseconds"));
                                if (totalMillis >= currentMillis) {
                                    JsonObject jsonDoc = new JsonObject()
                                            .put("start_time", formattedStartTime)
                                            .put("end_time", formattedEndTime);
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
        System.out.println("slotsList "+slotsList);
        return slotsList;
    }

    private JsonObject documentToJsonObject(Document doc) {
        JsonObject jsonObject = new JsonObject();
        for (String key : doc.keySet()) {
            jsonObject.put(key, doc.get(key));
        }
        return jsonObject;
    }

    private List<JsonObject> getBookedSlots(String studentId, String date) {
        List<JsonObject> bookedSlotList = new ArrayList<>();

        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Booked_slots");
            long inputDateMillis = timeUtility.dateToMilliseconds(date);
            Document bookedSlotsQuery = new Document("date_milliseconds", inputDateMillis)
                    .append("student_id", studentId)
                    .append("slot_status", new Document("$ne", "Cancelled"));

            try (MongoCursor<Document> cursor = collection.find(bookedSlotsQuery).iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    LOGGER.info("Found document: {}", doc.toJson());
                    bookedSlotList.add(documentToJsonObject(doc));
                }
            } catch (Exception e) {
                LOGGER.error("Error while fetching booked slots", e);
            }
        } else {
            LOGGER.error("Failed to connect to the database getBookedSlots().");
        }
        return bookedSlotList;
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
        String studentId = ctx.user().principal().getString("id");
        System.out.println(studentId);

        if (counsellor_id == null || date == null || studentId == null) {
            ctx.response()
                    .putHeader("content-type", "text/plain")
                    .setStatusCode(400)
                    .end("studentId, Counsellor ID and Date are required in slots.");
            return;
        }

        List<JsonObject> bookedSlotList = getBookedSlots(studentId, date);

        if (!bookedSlotList.isEmpty()){
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Only one slot can be booked for a day"));
            return;
        }
        else {
            List<JsonObject> dateList = getDates(counsellor_id, date);
            if (!dateList.isEmpty() && counsellorFoundGetDates)
            {
                counsellorFoundGetDates=false;
                ctx.response().putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(dateList));
                return;
            }
            else if(counsellorFoundGetDates && dateList.isEmpty()) {
                counsellorFoundGetDates=false;
                ctx.response().putHeader("content-type", "application/json")
                        .end(Json.encodePrettily("Slots are not available for the day"));
                return;
            }
        }

        long inputDateMillis = timeUtility.dateToMilliseconds(date);
        long currentMillis = timeUtility.currentDateAndTimeMillis();
        List<JsonObject> slotsList = new ArrayList<>();

        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Available_slots");
            Document query = new Document("date_milliseconds", inputDateMillis);

            try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
                boolean counselorFound = false;

                if (!cursor.hasNext()) {
                    List<JsonObject> primeSlotList = primeGetSlots(counsellor_id, date);

                    if (primeSlotList.isEmpty()) {
                        ctx.response().putHeader("content-type", "application/json")
                                .end(Json.encodePrettily("Slots are not created by the admin."));
                        return;
                    } else {
                        ctx.response().putHeader("content-type", "application/json")
                                .end(Json.encodePrettily(primeSlotList));
                        return;
                    }
                } else {
                    while (cursor.hasNext()) {
                        Document document = cursor.next();
                        List<Document> counselors = (List<Document>) document.get("counsellors");
                        for (Document counselor : counselors) {
                            String counselorId = counselor.getString("counsellor_id");
                            if (counsellor_id.equals(counselorId)) {
                                counselorFound = true;
                                List<Document> slots = (List<Document>) counselor.get("slots");
                                for (Document slot : slots) {
                                    boolean status = slot.getBoolean("status");
                                    if (!status) {
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
                    }
                }

                if (!counselorFound) {
                    List<JsonObject> primeSlotList = primeGetSlots(counsellor_id, date);
                    if (primeSlotList.isEmpty()) {
                        ctx.response().putHeader("content-type", "application/json")
                                .end(Json.encodePrettily("Slots are not available"));
                        return;
                    }
                    else {
                        ctx.response().putHeader("content-type", "application/json")
                                .end(Json.encodePrettily(primeSlotList));
                        return;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error while fetching slots", e);
            }
        } else {
            LOGGER.error("Failed to connect to the database in getSlots().");
        }

        if (slotsList.isEmpty()) {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Slots are not available"));
        } else {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(slotsList));
        }
    }

    public void getStudentInfo(RoutingContext ctx) {

        // Hardcoded student_id for demonstration purposes
        //String student_id = ctx.request().getParam("student_id");
        /*if (student_id == null) {
            ctx.response()
                    .putHeader("content-type", "text/plain")
                    .setStatusCode(400)
                    .end("Student ID is required.");
            return;
        }*/
        //session
        String student_id = ctx.user().principal().getString("id");
        System.out.println("student_id " + student_id);

        if (student_id == null) {
            ctx.response()
                    .putHeader("content-type", "text/plain")
                    .setStatusCode(400)
                    .end("Student ID is required.");
            return;
        }

        List<JsonObject> student_details = new ArrayList<>();
        if (database != null) {
            MongoCollection<Document> collection = database.getCollection("Student");

            Document query = new Document("_id", student_id).append("active", true);
            MongoCursor<Document> cursor = collection.find(query).iterator();

            try {
                if (cursor.hasNext()) {
                    Document studentDocument = cursor.next();

                    JsonObject studentInfo = new JsonObject()
                            .put("Register_No", studentDocument.getString("_id"))
                            .put("name", studentDocument.getString("name"))
                            .put("department", studentDocument.getString("department"))
                            .put("course", studentDocument.getString("course"))
                            .put("gender", studentDocument.getString("gender"))
                            .put("email", studentDocument.getString("email"));
                    student_details.add(studentInfo);
                } else {
                    LOGGER.error("Error while fetching student information, Student not found");
                    ctx.response().putHeader("content-type", "application/json")
                            .setStatusCode(500)
                            .end(Json.encodePrettily("Internal Server Error"));
                    return;
                }
            } finally {
                cursor.close();
            }
        } else {
            LOGGER.error("Failed to connect to the database in getStudentInfo().");
            ctx.response().putHeader("content-type", "application/json")
                    .setStatusCode(500)
                    .end(Json.encodePrettily("Internal Server Error"));
        }
        ctx.response().putHeader("content-type", "application/json")
                .end(Json.encodePrettily(student_details));
    }
}