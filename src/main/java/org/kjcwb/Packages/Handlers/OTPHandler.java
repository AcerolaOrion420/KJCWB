package org.kjcwb.Packages.Handlers;
import io.vertx.ext.web.RoutingContext;
import org.kjcwb.Packages.Services.EmailService;
import org.kjcwb.Packages.Services.RedisService;

import java.util.Objects;
import java.util.Random;

public class OTPHandler {
    private static final Random random = new Random();

    public static void generateOTP(RoutingContext ctx) {
        String email = ctx.getBodyAsJson().getString("email");
        String otp = String.format("%06d", random.nextInt(999999));

        // Store OTP in Redis
        RedisService.storeOtp(email, otp);

        // Send OTP via email
        EmailService.sendEmail(email, "Your OTP Code", "Your OTP code is: " + otp);

        ctx.response()
                .putHeader("content-type", "application/json")
                .end("{\"message\":\"OTP sent successfully\"}");
    }

    public static void verifyOTP(RoutingContext ctx) {
        String email = ctx.getBodyAsJson().getString("email");
        String otp = ctx.getBodyAsJson().getString("otp");

        // Retrieve OTP from Redis
        String sOtp = RedisService.getOtp(email);
        if (Objects.equals(otp, sOtp)) {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end("{\"message\":\"OTP is valid\"}");
        } else {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end("{\"message\":\"Invalid OTP\"}");
        }
        }

    public static boolean verifyOTP(String email, String otp) {

        // Retrieve OTP from Redis
        String sOtp = RedisService.getOtp(email);
        RedisService.close();
        return Objects.equals(otp, sOtp);
    }

    }
