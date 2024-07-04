package org.kjcwb.Packages.Handlers;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.web.RoutingContext;
import org.kjcwb.Packages.Services.RedisService;

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
        String email = context.user().principal().getString("email");

        // Renew TTL in Redis
        RedisService.storeJwt("jwt"+email, context.request().getHeader("Authorization").substring(7));

        context.next();
    }

    public static void handleProtectedRoute(RoutingContext context) {
        // This will be called only if the JWT token is valid
        context.response()
                .putHeader("content-type", "application/json")
                .end("{\"message\":\"Access granted\"}");
    }
    public static String generateToken(String email,String role) {
        return jwtAuth.generateToken(
                new JsonObject().put("email", email).put("role",role),
                new JWTOptions().setExpiresInMinutes(60));
    }
}
