package org.kjcwb.Packages;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.kjcwb.Packages.Services.DBConnectivity;
import org.kjcwb.Packages.Services.MongoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AGgrid {
    private final MongoDatabase database;
    private static final Logger LOGGER = LoggerFactory.getLogger(AGgrid.class);

    public AGgrid() {
        database = DBConnectivity.connectToDatabase("admin");
        if (database != null) {
            LOGGER.info("Slots class initialized and connected to DB");
        } else {
            LOGGER.error("Slots class failed to connect to the database");
        }
    }
    public void fetchAgGridData(RoutingContext ctx) {
        String id = ctx.user().principal().getString("id");
        String role=ctx.user().principal().getString("role");
        String queryid ="";

        if(Objects.equals(role, "user"))
        {
            queryid="student_id";
        }
        else {
            queryid="counsellor_id";
        }

        MongoCollection<Document> bookedSlotsCollection = database.getCollection("Booked_slots");

        // Query to fetch sessions for the hardcoded counselor ID
        Document query = new Document(queryid, id)
                .append("slot_status", new Document("$in", List.of("completed", "missed", "Cancelled")));

        FindIterable<Document> bookedSlots = bookedSlotsCollection.find(query);

        List<JsonObject> responseList = new ArrayList<>();

        for (Document bookedSlot : bookedSlots) {
            String studentId = bookedSlot.getString("student_id");
            Document studentQuery = new Document("_id", studentId);
            Document student;
            try {
                student = MongoService.find("Student", String.valueOf(studentQuery));
            } catch (Exception e) {
                    ctx.response().setStatusCode(400).putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(new JsonObject().put("error", "Failed to fetch student details")));
                    return;
            }

            if (student != null) {
                JsonObject record = new JsonObject();
                record.put("Name", student.getString("name"));
                record.put("date", bookedSlot.getString("date"));
                record.put("bookingId", bookedSlot.getString("_id"));
                record.put("status", bookedSlot.getString("slot_status"));
                responseList.add(record);
            }
        }

        ctx.response().setStatusCode(200).putHeader("content-type", "application/json")
                .end(Json.encodePrettily(responseList));
    }

}
