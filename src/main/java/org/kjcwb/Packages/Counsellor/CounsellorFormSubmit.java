package org.kjcwb.Packages.Counsellor;
import org.kjcwb.Packages.Services.*;
import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;


public class CounsellorFormSubmit {

    private MongoDatabase database;

    public CounsellorFormSubmit() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            System.out.println("Successfully connected to database");
        } else {
            System.out.println("Failed to connect to database");
        }
    }

    public Document findSingleDocument(String collectionName, Document query) {
        try {
            MongoCollection<Document> collection = database.getCollection(collectionName);
            FindIterable<Document> documents = collection.find(query);
            return documents.first();
        } catch (MongoException mongoException) {
            throw new RuntimeException("Mongo exception occurred while fetching document: " + mongoException.getMessage());
        }
    }

    public String timeFormatter(long milliseconds) {
        LocalTime time = LocalTime.ofNanoOfDay(milliseconds * 1_000_000);
        DateTimeFormatter timeformatter = DateTimeFormatter.ofPattern("hh:mm a");
        return time.format(timeformatter);
    }

    public long countCompletedSessions(String studentId) {
        MongoCollection<Document> collection = database.getCollection("Booked_slots");
        Document query = new Document("student_id", studentId).append("slot_status", "completed");
        return collection.countDocuments(query);
    }

    public List<String> findAllCompletedSessions(String studentId) {
        MongoCollection<Document> collection = database.getCollection("Booked_slots");
        Document query = new Document("student_id", studentId).append("slot_status", "completed");
        FindIterable<Document> documents = collection.find(query);

        List<String> counselorIds = new ArrayList<>();
        for (Document doc : documents) {
            counselorIds.add(doc.getString("counsellor_id"));
        }
        return counselorIds;
    }

    public String findCounselorName(String counselorId) {
        Document query = new Document("_id", counselorId);
        Document counselor = findSingleDocument("Counsellors", query);
        if (counselor != null) {
            return counselor.getString("name");
        } else {
            throw new RuntimeException("Counsellor not found");
        }
    }

    public void fetchStudent(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.emptyList()));
            return;
        }
        String student_id = requestBody.getString("student_id");
        String _id = requestBody.getString("_id");
        Document query = new Document("_id", _id);

        Document session;

        try {
            MongoService.initialize("Booked_slots");
            System.out.println("_id "+_id);
            session = MongoService.find("_id", _id);
        } catch (RuntimeException e) {
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new JsonObject().put("error", "Failed to fetch session")));
            return;
        }

        if (session != null) {
            long completedSessions = countCompletedSessions(student_id);
            List<String> counselorIds = findAllCompletedSessions(student_id);
            Set<String> counselorNames = new HashSet<>();

            for (String counselorId : counselorIds) {
                try {
                    String name = findCounselorName(counselorId);
                    counselorNames.add(name);
                } catch (RuntimeException e) {
                    ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(new JsonObject().put("error", "Failed to fetch counselor names")));
                    return;
                }
            }
            Document studentQuery = new Document("_id", student_id);
            Document student = findSingleDocument("Student", studentQuery);
            if (student == null) {
                ctx.response().setStatusCode(404).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(new JsonObject().put("error", "Student not found")));
                return;
            }

            List<String> uniqueCounselorNames = new ArrayList<>(counselorNames);

            session.put("slot_start_time_milliseconds", timeFormatter(session.getInteger("slot_start_time_milliseconds")));
            session.put("slot_end_time_milliseconds", timeFormatter(session.getInteger("slot_end_time_milliseconds")));

            session.put("student_id", student.getString("_id"));
            session.put("student_name", student.getString("name"));
            session.put("student_sem", student.getString("sem"));
            session.put("student_department", student.getString("department"));
            session.put("student_course", student.getString("course"));
            session.put("student_gender", student.getString("gender"));
            session.put("student_email", student.getString("email"));
            session.put("student_active", student.getBoolean("active"));
            session.put("completedSessions", completedSessions);

            JsonObject response = new JsonObject()
                    .put("session", new JsonObject(session.toJson()))
                    .put("completedSessions", completedSessions)
                    .put("counselorNames", uniqueCounselorNames);

            ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                    .end(response.encodePrettily());
            System.out.println("Fetched properly");

        } else {
            ctx.response().setStatusCode(404).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new JsonObject().put("error", "No session found")));
            System.out.println("Fetch unsuccessful ");
        }
    }

    public void updateSession(RoutingContext ctx) {
        JsonObject requestBody = ctx.getBodyAsJson();
        if (requestBody == null) {
            ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(Collections.emptyList()));
            return;
        }
        String _id = requestBody.getString("_id");
        boolean rootToggle = requestBody.getBoolean("rootToggle");
        System.out.println("rootToggle :" + rootToggle);

        Document query = new Document("_id", _id);

        Document session;
        try {
            session = findSingleDocument("Booked_slots", query);
        } catch (RuntimeException e) {
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new JsonObject().put("error", "Failed to fetch session")));
            return;
        }

        if (session != null) {
            String slotStatus = session.getString("slot_status");
            if ("completed".equals(slotStatus) || "Missed".equals(slotStatus)) {
                ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(new JsonObject().put("error", "Session already submitted")));
                return;
            }
            Document newReport = new Document();
            newReport.put("counsellor_id", requestBody.getString("counsellor_id"));
            newReport.put("_id", _id);
            newReport.put("SessionNo", requestBody.getString("SessionNo"));

            if (rootToggle) {
                boolean PastMedicalHistoryTogglevalue = requestBody.getBoolean("PastMedicalHistoryToggle");
                boolean PastPsychiatricHistoryTogglevalue = requestBody.getBoolean("PastPsychiatricHistoryToggle");
                boolean FurtherReferralsTogglevalue = requestBody.getBoolean("FurtherReferralsToggle");
                boolean FollowUpSessionTogglevalue = requestBody.getBoolean("FollowUpSessionToggle");
                System.out.println("PastMedicalHistoryTogglevalue :" + PastMedicalHistoryTogglevalue);
                System.out.println("PastPsychiatricHistoryTogglevalue :" + PastPsychiatricHistoryTogglevalue);
                System.out.println("FurtherReferralsTogglevalue :" + FurtherReferralsTogglevalue);
                System.out.println("FollowUpSessionTogglevalue :" + FollowUpSessionTogglevalue);

                newReport.put("PastMedicalHistory", requestBody.getBoolean("PastMedicalHistoryToggle") ?
                        requestBody.getString("PastMedicalHistory") : "NO DATA");
                newReport.put("PastPsychiatricHistory", requestBody.getBoolean("PastPsychiatricHistoryToggle") ?
                        requestBody.getString("PastPsychiatricHistory") : "NO DATA");
                newReport.put("FurtherReferrals", requestBody.getBoolean("FurtherReferralsToggle") ?
                        requestBody.getString("FurtherReferrals") : "NO DATA");
                newReport.put("RecommendedFollowUpSession", requestBody.getBoolean("FollowUpSessionToggle") ?
                        requestBody.getBoolean("RecommendedFollowUpSession") : false);
                newReport.put("ConcernsDiscussed", requestBody.getString("ConcernsDiscussed"));
                MongoCollection<Document> collection = database.getCollection("Report");
                collection.insertOne(newReport);

                MongoCollection<Document> bookedSlotsCollection = database.getCollection("Booked_slots");
                bookedSlotsCollection.updateOne(query, Updates.set("slot_status", "completed"));

            } else {
                MongoCollection<Document> collection = database.getCollection("Report");
                collection.insertOne(newReport);
                MongoCollection<Document> bookedSlotsCollection = database.getCollection("Booked_slots");
                bookedSlotsCollection.updateOne(query, Updates.set("slot_status", "Missed"));
            }

            ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new JsonObject().put("status", "Session updated successfully")));
            System.out.println(newReport);
        } else {
            ctx.response().setStatusCode(404).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new JsonObject().put("error", "Session not found")));
        }
    }
}