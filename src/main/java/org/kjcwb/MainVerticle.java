package org.kjcwb;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.kjcwb.Packages.Handlers.*;
import org.kjcwb.Packages.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        try {
            JwtAuthProvider.initialize(vertx);
        } catch (Exception e) {
            logger.error("Failed to initialize JwtAuthProvider: " + e.getMessage(), e);
            startPromise.fail("Failed to initialize JwtAuthProvider: " + e.getMessage());
            return;
        }

        // Enable CORS
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization"));

        // Enable BodyHandler to handle JSON bodies
        router.route().handler(BodyHandler.create());

        // Routes for Login
        router.post("/generate").handler(OTPHandler::generateOTP);
        router.post("/verify").handler(OTPHandler::verifyOTP);
        router.post("/login").handler(LoginHandler::handleLogin);
        router.post("/reset").handler(LoginHandler::handleReset);
        router.post("/forget").handler(LoginHandler::handleForget);
        router.post("/userverify").handler(LoginHandler::handleUserLogin);

        // Routes protection
        router.route("/protected/*").handler(JWTAuthHandler.create(JwtAuthProvider.getJwtAuth()));
        router.route("/protected/*").handler(JwtAuthProvider::handleTokenRenewal);
        router.get("/protected/test").handler(Test::handleTest);

        // Routes for User
        router.route("/protected/user/*").handler(RoleHandler::handleUserRole);
        router.get("/protected/user/resource").handler(JwtAuthProvider::handleProtectedRoute);
        router.post("/protected/user/sessions").handler(StudentUpcomingSession::getUpcomingsession);

        // Routes for Counsellor
        router.route("/protected/counsellor/*").handler(RoleHandler::handleCounsellorRole);
        router.get("/protected/counsellor/resource").handler(JwtAuthProvider::handleProtectedRoute);
        router.post("/protected/counsellor/updatePhoneNumber").handler(UpdatePhoneNumber::handleUpdatePhoneNumber);
        router.post("/protected/counsellor/getprofiledetails").handler(FetchProfileDetails::handleFetchProfileDetails);

        // Routes for Admin
        router.route("/protected/admin/*").handler(RoleHandler::handleAdminRole);
        router.get("/protected/admin/resource").handler(JwtAuthProvider::handleProtectedRoute);

        vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                logger.info("HTTP server started on port 8888");
            } else {
                logger.error("Failed to start HTTP server: " + http.cause().getMessage(), http.cause());
                startPromise.fail("Failed to start HTTP server: " + http.cause().getMessage());
            }
        });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle(), res -> {
            if (res.succeeded()) {
                logger.info("MainVerticle deployed successfully.");
            } else {
                logger.error("Deployment Failed: " + res.cause().getMessage(), res.cause());
            }
        });
    }
}
