package org.kjcwb.Packages.Services;

import com.mongodb.client.*;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MongoService {
    private static MongoClient mongoClient;
    private static MongoCollection<Document> collection;

    public static void initialize(String connectionString, String dbName, String collectionName) {
        mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase(dbName);
        collection = database.getCollection(collectionName);
    }

    public static void insert(Document document) {
        collection.insertOne(document);
    }

    public static Document find(String key, String value) {
        return collection.find(new Document(key, value)).first();
    }

    public static Document find(String key, int value) {
        return collection.find(new Document(key, value)).first();
    }

    public static List<Document> findall (Document query) {
        // Find documents matching the query
        FindIterable<Document> result = collection.find(query);

        // Aggregate the matching documents into a list
        List<Document> documentsList = new ArrayList<>();
        for (Document document : result) {
            documentsList.add(document);
        }
        return documentsList;
    }

    public static void update(String idkey,String idvalue,String updatekey, String updatevalue) {
        collection.updateOne(new Document(idkey, idvalue), Updates.set(updatekey, updatevalue));
    }

    public static void close() {
        mongoClient.close();
    }

}

