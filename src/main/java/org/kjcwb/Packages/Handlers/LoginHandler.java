package org.kjcwb.Packages.Handlers;


import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.kjcwb.Packages.Services.MongoService;
import org.kjcwb.Packages.Services.RedisService;
import org.mindrot.jbcrypt.BCrypt;

public class LoginHandler {

    public static void handleLogin(RoutingContext context) {
        MongoService.initialize( "Counsellor");
        String email = context.getBodyAsJson().getString("email");
        String password = context.getBodyAsJson().getString("password");

        Document user = MongoService.find("email", email);
        System.out.println(JwtAuthProvider.getJwtAuth());

        if (user != null && BCrypt.checkpw(password, user.getString("password"))) {
            String token = JwtAuthProvider.generateToken(email,user.getString("role"),user.getString("_id"));
            RedisService.storeJwt("jwt"+email, token);

            context.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("token", token).put("role",user.get("role")).put("message","Successfully Logged in").encode());
        } else {
            context.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(401)
                    .end(new JsonObject().put("message", "Invalid credentials").encode());
        }
        MongoService.close();
    }

    public static void handleUserLogin(RoutingContext ctx){
        String email = ctx.getBodyAsJson().getString("email");
        String otp = ctx.getBodyAsJson().getString("otp");
        MongoService.initialize(  "Student");
        Document user = MongoService.find("email", email);

        if (OTPHandler.verifyOTP(email, otp)) {
            String token = JwtAuthProvider.generateToken(email,"user",user.getString("_id"));
            RedisService.storeJwt("jwt"+email, token);
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("token", token).put("message","OTP is valid").put("role","user").encode());
        } else {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end("{\"message\":\"Invalid OTP\"}");
        }
        MongoService.close();
    }

    public static void handleReset(RoutingContext context){
        MongoService.initialize("Counsellor");
        String email = context.getBodyAsJson().getString("email");
        String password = context.getBodyAsJson().getString("password");
        String newpassword = context.getBodyAsJson().getString("newpassword");
        String hashpwd = BCrypt.hashpw(newpassword, BCrypt.gensalt());
        Document user = MongoService.find("email", email);

        if (BCrypt.checkpw(password,user.getString("password"))) {
            MongoService.update("email",email,"password",hashpwd);
            context.response()
                    .putHeader("content-type", "application/json")
                    .end("{\"message\":\"Password Updated\"}");
        } else {
            context.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(401)
                    .end("{\"message\":\"Updation failed, check password\"}");
        }
        MongoService.close();
    }
    public static void handleForget(RoutingContext context){
        MongoService.initialize("Counsellor");
        String email = context.getBodyAsJson().getString("email");
        String newpassword = context.getBodyAsJson().getString("newpassword");
        String hashpwd = BCrypt.hashpw(newpassword, BCrypt.gensalt());
        System.out.println(newpassword);
        System.out.println(email);
        Document user = null;
        user = MongoService.find("email", email);
        if ((user != null) && (newpassword != null)){
            MongoService.update("email",email,"password",hashpwd);
            context.response()
                    .putHeader("content-type", "application/json")
                    .end("{\"message\":\"Password Updated\"}");
        } else {
            context.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(401)
                    .end("{\"message\":\"Updation failed, check password\"}");
        }
        MongoService.close();
    }
}
