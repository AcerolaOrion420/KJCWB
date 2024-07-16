package org.kjcwb.Packages.Counsellor;
import org.kjcwb.Packages.Services.MongoService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class UpdatePhoneNumber {
    public static void handleUpdatePhoneNumber(RoutingContext context) {
        MongoService.initialize( "Counsellor");
        JsonObject requestBody = context.getBodyAsJson();
        if (requestBody == null) {
            context.response().setStatusCode(400).putHeader("content-type", "application/json").end();
            return;
        }
        String email = context.user().principal().getString("email");
        String newPhoneNumber = requestBody.getString("phoneNo");

        if (email == null || newPhoneNumber == null) {
            context.response()
                    .putHeader("content-type", "text/plain")
                    .setStatusCode(400)
                    .end("employeeId and newPhoneNumber are required.");
            return;
        }

        try {
            MongoService.update( "email", email, "phoneNo", newPhoneNumber);
            context.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject().put("message", "Phone number updated successfully").encode());
        } catch (Exception e) {
            context.response()
                    .setStatusCode(500) // Internal Server Error
                    .end(new JsonObject().put("error", "Failed to update phone number: " + e.getMessage()).encode());
        }
    }
}