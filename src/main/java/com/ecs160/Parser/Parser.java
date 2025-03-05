package com.ecs160.Parser;

import com.ecs160.BlueSkySchema.Post;
import com.google.gson.*;

import java.io.*;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.Stack;


import static java.lang.System.exit;

/*
 * Recursively parse posts and their replies from the BlueSky JSON dump into a Java object tree.
 */
public class Parser {
    // id counter to assign unique id to each parsed post
    private int idCounter = 0;

    /**
     * Recursively parse all the threads and replies that exist in the JSON file into Java objects
     * @param jsonFileName name of the JSON file to parse
     * @return List of parsed threads from the JSON file
     */
    public LinkedList<Post> parseThreads(boolean isInternal, String jsonFileName) throws Exception {
        JsonElement element;

        // Check whether provided JSON file is internal (in resources) or external
        if (isInternal) {
            InputStream jsonStream = JsonDeserializer.class.getClassLoader().getResourceAsStream(jsonFileName);
            if (jsonStream == null) {
                throw new ParserException("Resource JSON file \"" + jsonFileName + "\" not found");
            }

            element = JsonParser.parseReader(new InputStreamReader(jsonStream));
        } else {
            // Implicitly throws FileNotFoundException if filepath not found
            FileReader jsonFile = new FileReader(jsonFileName);
            BufferedReader bufJsonReader = new BufferedReader(jsonFile);
            element = JsonParser.parseReader(bufJsonReader);
        }

        LinkedList<Post> threads = new LinkedList<Post>();

        if (!element.isJsonObject()) {
            throw new ParserException("Root JSON element is not an object");
        }
        JsonObject jsonObject = element.getAsJsonObject();

        if (!jsonObject.has("feed") || !jsonObject.get("feed").isJsonArray()) {
            throw new ParserException("JSON does not contain array named \"feed\"");
        }
        JsonArray feedArray = jsonObject.get("feed").getAsJsonArray();

        for (JsonElement feedObject : feedArray) {
            // Parse top-level thread objects
            JsonObject feedObjectJson = feedObject.getAsJsonObject();
            if (feedObjectJson.has("thread") && feedObjectJson.get("thread").isJsonObject()) {
                JsonObject thread = feedObjectJson.get("thread").getAsJsonObject();

                /*
                 * In case there are unexpected thread objects that are either missing fields or have fields with
                 * unexpected data types, then simply skip the current post.
                 */
                if (!thread.has("post") || !thread.get("post").isJsonObject()) {
                    continue;
                }
                JsonObject post = thread.get("post").getAsJsonObject();

                if (!post.has("record") || !post.get("record").isJsonObject()) {
                    continue;
                }
                JsonObject record = post.get("record").getAsJsonObject();

                if (!record.has("createdAt")) {
                    continue;
                }

                int id = getUniqueId();
                Post postObj = new Post(
                        id,
                        record.get("createdAt").getAsString(),
                        record.has("text") ? record.get("text").getAsString() : ""
                );

                if (thread.has("replies") && thread.get("replies").isJsonArray()) {
                    JsonArray replies = thread.get("replies").getAsJsonArray();
                    for (JsonElement reply : replies) {
                        postObj.addReply(parseReply(reply));
                    }
                }

                threads.add(postObj);
            }
        }

        return threads;
    }

    /**
     * Parse a single reply
     * @param jsonReply reply to parse
     * @return parsed Reply object
     */
    public Post parseReply(JsonElement jsonReply) {
            JsonObject reply = jsonReply.getAsJsonObject();
            if (!reply.has("post") || !reply.get("post").isJsonObject()) {
                System.out.println("Invalid JSON");
                exit(0);
            }
            JsonObject post = reply.get("post").getAsJsonObject();

            if (!post.has("record") || !post.get("record").isJsonObject()) {
                System.out.println("Invalid JSON");
                exit(0);
            }
            JsonObject record = post.get("record").getAsJsonObject();

            if (!record.has("createdAt")) {
                System.out.println("Invalid JSON");
                exit(0);
            }

            int id = getUniqueId();
            Post newReply = new Post(
                    id,
                    record.get("createdAt").getAsString(),
                    record.has("text") ? record.get("text").getAsString() : ""
            );

            return newReply;
    }

    /**
     * Get a new unique id from this current Parser object
     * @return new unique id
     */
    private int getUniqueId() {
        int nextId = idCounter;
        idCounter += 1;
        return nextId;
    }
}
