package org.kjcwb.Packages;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.kjcwb.Packages.Services.MongoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class Counsellors {
    private static final Logger LOGGER = LoggerFactory.getLogger(Counsellors.class);


    // Gets all the Counsellors who are active in DB
    public static void getCounsellor(RoutingContext ctx) {
        List<Document> counsellorList = new ArrayList<>();

        try {
            // Query to find documents where "Active" is true
            Document query = new Document("Active", true).append("role","counsellor");
            MongoService.initialize("mongodb://localhost:27017", "admin", "Counsellor");
            // Fetch all documents matching the query
            List<Document> documents = MongoService.findall(query);

            for (Document doc : documents) {
                Document counsellor = new Document("counsellor_id", doc.getString("_id"))
                        .append("name", doc.getString("name"));
                counsellorList.add(counsellor);
            }

            ctx.response().putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(counsellorList));

        } catch (Exception e) {
            LOGGER.error("Error fetching counsellors: {}", e.getMessage(), e);
            ctx.response().setStatusCode(500).putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(new Document("error", "Internal Server Error")));
        }
    }
}