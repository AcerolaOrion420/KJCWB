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

public class Slots {
    private static final Logger LOGGER = LoggerFactory.getLogger(Slots.class);
    private final TimeUtility timeUtility = new TimeUtility();
    private boolean counsellorFoundGetDates = false;
    
    
    private List<JsonObject> getDates(String counsellorId, String date) {
        List<JsonObject> dateList = new ArrayList<>();
        long inputDateMillis = timeUtility.dateToMilliseconds(date);
        int range = getRange();

        if (range <= 0) {
            LOGGER.error("Date frame for booking cannot be less than or equal to 0, please check the Date_Range collection");
            return dateList;
        }

        MongoService.initialize(  "Counsellor_Leave_Calendar");
        try {
            Document query = new Document("date_milliseconds", inputDateMillis);
            List<Document> results = MongoService.findall(query);

            for (Document doc : results) {
                List<Document> counsellors = (List<Document>) doc.get("counsellors");
                for (Document counsellor : counsellors) {
                    if (counsellor.getString("counsellor_id").equals(counsellorId) && counsellor.getBoolean("inactive")) {
                        counsellorFoundGetDates = true;
                        List<Document> slots = (List<Document>) counsellor.get("slots");
                        if (slots != null) {
                            for (Document slot : slots) {
                                if (!slot.getBoolean("status")) {
                                    String formattedStartTime = timeUtility.timeFormatter(slot.getInteger("slot_s_time_m"));
                                    String formattedEndTime = timeUtility.timeFormatter(slot.getInteger("slot_e_time_m"));
                                    JsonObject slotJson = new JsonObject()
                                            .put("slot_s_time", formattedStartTime)
                                            .put("slot_e_time", formattedEndTime);
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
        } finally {
            MongoService.close();
        }
        return dateList;
    }

    private int getRange() {
        MongoService.initialize(  "Date_Range");
        try {
            Document result = MongoService.find("range", 1);
            if (result != null) {
                return result.getInteger("range");
            } else {
                LOGGER.error("No document found in the 'Range' collection.");
                return -1;
            }
        } finally {
            MongoService.close();
        }
    }

    private List<JsonObject> primeGetSlots(String cid, String date) {
        List<JsonObject> slotsList = new ArrayList<>();
        long inputDateMillis = timeUtility.dateToMilliseconds(date);
        long currentMillis = timeUtility.currentDateAndTimeMillis();

        MongoService.initialize(  "Counsellor_Default_Slots");
        try {
            Document doc = MongoService.find("counselor_id", cid);
            if (doc != null) {
                List<Document> slots = (List<Document>) doc.get("slots");
                if (slots != null) {
                    for (Document slot : slots) {
                        boolean status = slot.getBoolean("status");
                        if (!status) {
                            int slotStartMillis = slot.getInteger("slot_s_time_m");
                            long totalMillis = inputDateMillis + slotStartMillis;

                            String formattedStartTime = timeUtility.timeFormatter(slot.getInteger("slot_s_time_m"));
                            String formattedEndTime = timeUtility.timeFormatter(slot.getInteger("slot_e_time_m"));
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
        } finally {
            MongoService.close();
        }
        return slotsList;
    }

    private List<JsonObject> getBookedSlots(String studentId, String date) {
        List<JsonObject> bookedSlotList = new ArrayList<>();
        long inputDateMillis = timeUtility.dateToMilliseconds(date);

        MongoService.initialize("Booked_Slots");
        try {
            Document query = new Document("date_mils", inputDateMillis).append("student_id", studentId);
            List<Document> results = MongoService.findall(query);
            for (Document doc : results) {
                LOGGER.info("Found document: {}", doc.toJson());
                bookedSlotList.add(new JsonObject(doc.toJson()));
            }
        } catch (Exception e) {
            LOGGER.error("Error while fetching booked slots", e);
        } finally {
            MongoService.close();
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
        String cid = requestBody.getString("counsellor_id");
        String date = requestBody.getString("date");
        String studentId = ctx.user().principal().getString("id");

        System.out.println("studentId " + studentId);

        if (cid == null || date == null) {
            ctx.response()
                    .putHeader("content-type", "text/plain")
                    .setStatusCode(400)
                    .end("Counsellor ID and Date are required.");
            return;
        }

        List<JsonObject> bookedSlotList = getBookedSlots(studentId, date);
        System.out.println("bookedSlotList " + bookedSlotList);
        if (!bookedSlotList.isEmpty()) {
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Only one slot can be booked for a day"));
            return;
        }

        List<JsonObject> dateList = getDates(cid, date);
        if (!dateList.isEmpty() && counsellorFoundGetDates) {
            counsellorFoundGetDates = false;
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(dateList));
            return;
        } else if (counsellorFoundGetDates && dateList.isEmpty()) {
            counsellorFoundGetDates = false;
            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily("Slots are not available for the day"));
            return;
        }

        long inputDateMillis = timeUtility.dateToMilliseconds(date);
        long currentMillis = timeUtility.currentDateAndTimeMillis();
        List<JsonObject> slotsList = new ArrayList<>();

        MongoService.initialize(  "Appointment");
        try {
            Document query = new Document("date_mils", inputDateMillis);
            List<Document> results = MongoService.findall(query);

            if (results.isEmpty()) {
                List<JsonObject> primeSlotList = primeGetSlots(cid, date);
                if (primeSlotList.isEmpty()) {
                    ctx.response().putHeader("content-type", "application/json")
                            .end(Json.encodePrettily("Slots are not created by the admin."));
                } else {
                    ctx.response().putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(primeSlotList));
                }
                return;
            }

            boolean counselorFound = false;
            for (Document document : results) {
                List<Document> counselors = (List<Document>) document.get("counselor");
                for (Document counselor : counselors) {
                    String counselorId = counselor.getString("counselor_id");
                    if (cid.equals(counselorId)) {
                        counselorFound = true;
                        List<Document> slots = (List<Document>) counselor.get("slots");
                        for (Document slot : slots) {
                            boolean status = slot.getBoolean("status");
                            if (!status) {
                                int slotStartMillis = slot.getInteger("slot_s_time_m");
                                long totalMillis = inputDateMillis + slotStartMillis;
                                if (totalMillis >= currentMillis) {
                                    JsonObject jsonDoc = new JsonObject()
                                            .put("start_time", slot.getString("slot_s_time"))
                                            .put("end_time", slot.getString("slot_e_time"));
                                    slotsList.add(jsonDoc);
                                }
                            }
                        }
                    }
                }
            }

            if (!counselorFound) {
                List<JsonObject> primeSlotList = primeGetSlots(cid, date);
                if (primeSlotList.isEmpty()) {
                    ctx.response().putHeader("content-type", "application/json")
                            .end(Json.encodePrettily("Slots are not available"));
                } else {
                    ctx.response().putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(primeSlotList));
                }
                return;
            }
        } catch (Exception e) {
            LOGGER.error("Error while fetching slots", e);
        } finally {
            MongoService.close();
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
        MongoService.initialize(  "Student");
        try {
            Document query = new Document("_id", student_id).append("active", true);
            Document studentDocument = MongoService.find("_id", student_id);

            if (studentDocument != null && studentDocument.getBoolean("active", false)) {
                JsonObject studentInfo = new JsonObject()
                        .put("Register_No", studentDocument.getString("_id"))
                        .put("name", studentDocument.getString("name"))
                        .put("department", studentDocument.getString("department"))
                        .put("course", studentDocument.getString("course"))
                        .put("gender", studentDocument.getString("gender"))
                        .put("email", studentDocument.getString("email"));
                student_details.add(studentInfo);

                ctx.response().putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(student_details));
            } else {
                LOGGER.error("Error while fetching student information, Student not found or inactive");
                ctx.response().putHeader("content-type", "application/json")
                        .setStatusCode(404)
                        .end(Json.encodePrettily("Student not found or inactive"));
            }
        } catch (Exception e) {
            LOGGER.error("Error while fetching student information", e);
            ctx.response().putHeader("content-type", "application/json")
                    .setStatusCode(500)
                    .end(Json.encodePrettily("Internal Server Error"));
        } finally {
            MongoService.close();
        }
    }
}