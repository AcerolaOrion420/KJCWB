package org.kjcwb.Packages.Services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBConnectivity {
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static MongoClient mongoClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(DBConnectivity.class);
    static {
        mongoClient = MongoClients.create(CONNECTION_STRING);
    }

    public static MongoDatabase connectToDatabase(String databaseName) {
        try {
            // Access the specified database
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            System.out.println("Connected to the database successfully");
            return database;
        } catch (Exception e) {
            LOGGER.error("Error in saving: {}" ,e.getMessage());
            return null;
        }
    }

    public static void closeClient() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}