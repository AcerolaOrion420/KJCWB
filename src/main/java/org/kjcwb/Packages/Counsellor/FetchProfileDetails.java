package org.kjcwb.Packages.Counsellor;
import org.kjcwb.Packages.Services.MongoService;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;

import java.util.List;

public class FetchProfileDetails {

    public static void handleFetchProfileDetails(RoutingContext context) {
        MongoService.initialize("Counsellors");

        String email = context.user().principal().getString("email");

        if (email == null) {
            context.response()
                    .putHeader("content-type", "text/plain")
                    .setStatusCode(400)
                    .end("employeeId is required.");
            return;
        }

        Document query = new Document("email", email);
        try {
            List<Document> results = MongoService.findall(query);
            JsonArray jsonArray = new JsonArray();
            for (Document document : results) {
                JsonObject profileDetails = new JsonObject()
                        .put("name", document.getString("name"))
                        .put("employeeId", document.getString("_id"))
                        .put("gender", document.getString("gender"))
                        .put("dob", document.getString("dob"))
                        .put("email", document.getString("email"))
                        .put("phoneNo", document.getString("phoneNo"));

                jsonArray.add(profileDetails);

//                // Printing to console for debugging
//                System.out.println("Name: " + document.getString("name"));
//                System.out.println("Employee ID: " + document.getString("employeeId"));
//                System.out.println("Gender: " + document.getString("gender"));
//                System.out.println("DOB: " + document.getString("dob"));
//                System.out.println("Email: " + document.getString("email"));
//                System.out.println("Phone No: " + document.getString("phoneNo"));
//                System.out.println("------------------------------------");
            }

            context.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end(Json.encodePrettily(jsonArray));
        } catch (Exception e) {
            context.response()
                    .setStatusCode(500) // Internal Server Error
                    .end("Failed to fetch profile details: " + e.getMessage());
        }
    }
}
