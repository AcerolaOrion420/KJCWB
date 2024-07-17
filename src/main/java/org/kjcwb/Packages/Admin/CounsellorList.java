package org.kjcwb.Packages.Admin;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.kjcwb.Packages.Services.MongoService;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class CounsellorList {
    private static final Logger LOGGER = LoggerFactory.getLogger(CounsellorList.class);

    // Gets all the Counsellors who are active in DB
    public static void getCounsellor(RoutingContext ctx) {
        List<Document> counsellorList = new ArrayList<>();

        try {
            // Query to find documents where "Active" is true and role is "counsellor"
            Document query = new Document("Active", true).append("role", "counsellor");
            MongoService.initialize("Counsellor");

            // Fetch all documents matching the query
            List<Document> documents = MongoService.findall(query);

            for (Document doc : documents) {
                String firstName = doc.getString("name");
                String lastName = doc.getString("lname");
                String fullName = (firstName != null ? firstName : "") +" "+
                        (lastName != null ? " " + lastName : "").trim();

                Document counsellor = new Document("id", doc.getString("_id"))
                        .append("name", fullName)
                        .append("type", doc.getString("type"))
                        .append("email", doc.getString("email"))
                        .append("phone", doc.getString("phoneNo"));
                counsellorList.add(counsellor);
            }

            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(counsellorList));

        } catch (Exception e) {
            LOGGER.error("Error fetching counsellors: {}", e.getMessage(), e);
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new Document("error", "Internal Server Error")));
        } finally {
            MongoService.close();
        }
    }

    public static void addCounsellor(RoutingContext ctx) {
        try {
            JsonObject formData = ctx.getBodyAsJson();

            // Extract fields from the form data
            String firstName = formData.getString("first name");
            String lastName = formData.getString("last Name");
            String id = formData.getString("id");
            String type = formData.getString("type");
            String department = formData.getString("department");
            String qualification = formData.getString("qualification");
            String gender = formData.getString("gender");
            String phone = formData.getString("phone");
            String email = formData.getString("email");
            String hashpwd = BCrypt.hashpw(email, BCrypt.gensalt());

            // Create a new Document for the counsellor
            Document newCounsellor = new Document("_id", id)
                    .append("name", firstName)
                    .append("lname", lastName)
                    .append("type", type)
                    .append("password",hashpwd)
                    .append("department", department)
                    .append("qualification", qualification)
                    .append("gender", gender)
                    .append("phoneNo", phone)
                    .append("email", email)
                    .append("role", "counsellor")
                    .append("Active", true);

            // Initialize MongoDB connection
            MongoService.initialize("Counsellor");

            // Insert the new counsellor document
            MongoService.insert(newCounsellor);

            // Send success response
            ctx.response().setStatusCode(201).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new JsonObject().put("message", "Counsellor added successfully")));

        } catch (Exception e) {
            LOGGER.error("Error adding counsellor: {}", e.getMessage(), e);
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new JsonObject().put("error", "Internal Server Error")));
        } finally {
            MongoService.close();
        }
    }
}