package org.kjcwb.Packages;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class Test {
    public static void handleTest(RoutingContext context) {
        String userRole = context.user().principal().getString("role");
        String userEmail = context.user().principal().getString("email");
        String userID = context.user().principal().getString("id");
        System.out.println("Role: " +userRole);
        System.out.println("Email: " +userEmail);
        System.out.println("ID: " +userID);
        context.response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("Role", userRole).put("Email", userEmail).put("ID", userID).encode());
    }
}
