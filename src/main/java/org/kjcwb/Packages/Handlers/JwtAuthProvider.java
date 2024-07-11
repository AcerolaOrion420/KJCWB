package org.kjcwb.Packages.Handlers;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.web.RoutingContext;

public class JwtAuthProvider {
    private static JWTAuth jwtAuth;
    private static final String JWT_SECRET = System.getenv("JWT_SECRET");

    public static void initialize(Vertx vertx) {
        if (JWT_SECRET == null) {
            throw new RuntimeException("JWT_SECRET environment variable is not set.");
        }

        jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions().addPubSecKey(
                new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(JWT_SECRET)
        ));
    }

    public static JWTAuth getJwtAuth() {
        return jwtAuth;
    }

    public static void handleTokenRenewal(RoutingContext context) {
        String currentToken = context.request().getHeader("Authorization");
        if (currentToken != null && currentToken.startsWith("Bearer ")) {
            currentToken = currentToken.substring(7); // Remove "Bearer " prefix
        } else {
            context.response().setStatusCode(401).end("Invalid token format");
            return;
        }

        jwtAuth.authenticate(new JsonObject().put("token", currentToken), res -> {
            if (res.succeeded()) {
                // Token is still valid, proceed with renewal
                String email = res.result().principal().getString("email");
                String role = res.result().principal().getString("role");
                String id = res.result().principal().getString("id");

                // Generate a new token
                String newToken = generateToken(email, role, id);

                // Set the new token in the response header and body
                context.response()
                        .putHeader("Authorization", "Bearer " + newToken)
                        .end(new JsonObject().put("newtoken", newToken).encode());
            } else {
                context.response().setStatusCode(401).end("Token expired or invalid");
            }
        });
    }

    public static void handleProtectedRoute(RoutingContext context) {
        // This will be called only if the JWT token is valid
        context.response()
                .putHeader("content-type", "application/json")
                .end("{\"message\":\"Access granted\"}");
    }

    public static String generateToken(String email, String role, String id) {
        int expiryTime = getExpiryTimeForRole(role);
        return jwtAuth.generateToken(
                new JsonObject().put("email", email).put("role", role).put("id", id),
                new JWTOptions().setExpiresInMinutes(expiryTime));
    }

    private static int getExpiryTimeForRole(String role) {
        switch (role.toLowerCase()) {
            case "user":
                return 5;  // 5 minutes for users
            case "counsellor":
                return 15; // 15 minutes for counsellors
            case "admin":
                return 15; // 15 minutes for admins (you can adjust this as needed)
            default:
                return 5;  // Default to 5 minutes for unknown roles
        }
    }
}