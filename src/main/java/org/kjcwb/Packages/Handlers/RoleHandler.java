package org.kjcwb.Packages.Handlers;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class RoleHandler {

    public static void handleRole(String requiredRole, RoutingContext context) {
        String userRole = context.user().principal().getString("role");
        System.out.println("Role: "+userRole);
        if (requiredRole.equals(userRole)) {
            context.next();
        } else {
            context.response()
                    .setStatusCode(403)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("message", "Forbidden: Insufficient role").encode());
        }
    }

    public static void handleUserRole(RoutingContext context) {
        handleRole("user", context);
    }

    public static void handleCounsellorRole(RoutingContext context) {
        handleRole("counsellor", context);
    }

    public static void handleAdminRole(RoutingContext context) {
        handleRole("admin", context);
    }
}
