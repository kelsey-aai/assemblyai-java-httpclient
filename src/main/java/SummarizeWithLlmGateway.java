import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SummarizeWithLlmGateway {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("ASSEMBLYAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set the ASSEMBLYAI_API_KEY environment variable first.");
            System.exit(1);
        }
        if (args.length < 1) {
            System.err.println("Usage: SummarizeWithLlmGateway <transcript_id>");
            System.exit(1);
        }

        // LLM Gateway swaps {{ transcript }} for the text of the transcript you pass by ID
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content",
                "Summarize this transcript in three bullet points, then list any action items.\n\n{{ transcript }}");
        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", "claude-sonnet-4-6");
        body.add("messages", messages);
        body.addProperty("transcript_id", args[0]);
        body.addProperty("max_tokens", 1000);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://llm-gateway.assemblyai.com/v1/chat/completions"))
                .header("Authorization", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        String content = result.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
        System.out.println(content);
    }
}
