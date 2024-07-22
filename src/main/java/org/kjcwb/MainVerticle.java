package org.kjcwb;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.kjcwb.Packages.Admin.CounsellorList;
import org.kjcwb.Packages.Counsellor.*;
import org.kjcwb.Packages.Handlers.*;
import org.kjcwb.Packages.*;
import org.kjcwb.Packages.Student.AvailableSlotsForBooking;
import org.kjcwb.Packages.Student.BookASlot;
import org.kjcwb.Packages.Student.StudentSlotCancellation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.kjcwb.Packages.Student.StudentUpcomingSession;

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
                .allowedHeader("Authorization")
                .allowedHeader("newtoken"));


        // Enable BodyHandler to handle JSON bodies
        router.route().handler(BodyHandler.create());

        // Unprotected Routes
        router.post("/generate").handler(OTPHandler::generateOTP);
        router.post("/verify").handler(OTPHandler::verifyOTP);
        router.post("/login").handler(LoginHandler::handleLogin);
        router.post("/reset").handler(LoginHandler::handleReset);
        router.post("/forget").handler(LoginHandler::handleForget);
        router.post("/userverify").handler(LoginHandler::handleUserLogin);
        router.get("/counsellors").handler(ActiveCounsellors::getCounsellor);
        router.get("/counsellorlist").handler(CounsellorList::getCounsellor);
        router.post("/addcounsellor").handler(CounsellorList::addCounsellor);



        // Protected Routes
        AGgrid ag = new AGgrid();


        router.route("/protected/*").handler(JWTAuthHandler.create(JwtAuthProvider.getJwtAuth()));
        router.route("/protected/*").handler(JwtAuthProvider::handleTokenRenewal);
        router.get("/protected/aggrid").handler(ag::fetchAgGridData);




        // User Routes
        StudentUpcomingSession sus = new StudentUpcomingSession();
        AvailableSlotsForBooking slots = new AvailableSlotsForBooking();
        BookASlot bookslot = new BookASlot();
        StudentSlotCancellation scc = new StudentSlotCancellation();

        router.route("/protected/user/*").handler(RoleHandler::handleUserRole);
        router.get("/protected/user/sessions").handler(sus::getUpcomingSession);
        router.post("/protected/user/slots").handler(slots::getSlots);
        router.post("/protected/user/slotbooking").handler(bookslot::setSlots);
        router.get("/protected/user/getstudentinfo").handler(slots::getStudentInfo);
        router.post("/protected/user/slotcancel").handler(scc::slotCancellation);



        // Counsellor Routes
        CounsellorUpcomingSession cus = new CounsellorUpcomingSession();
        CounsellorBlockCalendar cbc = new CounsellorBlockCalendar();
        CounsellorBlockCalendarSubmit cbcs = new CounsellorBlockCalendarSubmit();
        CounsellorFormSubmit cfs = new CounsellorFormSubmit();


        router.route("/protected/counsellor/*").handler(RoleHandler::handleCounsellorRole);
        router.post("/protected/counsellor/sessions").handler(cus::getUpcomingSession);
        router.post("/protected/counsellor/updatePhoneNumber").handler(UpdatePhoneNumber::handleUpdatePhoneNumber);
        router.get("/protected/counsellor/getprofiledetails").handler(FetchProfileDetails::handleFetchProfileDetails);
        router.post("/protected/counsellor/availableslots").handler(cbc::getAvailableSlots);
        router.post("/protected/counsellor/blockedslots").handler(cbc::getBlockedSlots);
        router.post("/protected/counsellor/blockslots").handler(cbcs::blockSlots);
        router.post("/protected/counsellor/unblockslots").handler(cbcs::unblockSlots);
        router.post("/protected/counsellor/fetchstudent").handler(cfs::fetchStudent);
        router.post("/protected/counsellor/formsubmit").handler(cfs::updateSession);
        router.get("/protected/aggrid").handler(ag::fetchAgGridData);



        // Admin Routes
        router.route("/protected/admin/*").handler(RoleHandler::handleAdminRole);





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
