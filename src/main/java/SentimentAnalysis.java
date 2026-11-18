import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SentimentAnalysis {
    private static final String BASE_URL = "https://api.assemblyai.com";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("ASSEMBLYAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set the ASSEMBLYAI_API_KEY environment variable first.");
            System.exit(1);
        }
        String audioUrl = args.length > 0 ? args[0] : "https://assembly.ai/wildfires.mp3";

        JsonArray speechModels = new JsonArray();
        speechModels.add("assemblyai-stt-3-7");

        JsonObject body = new JsonObject();
        body.addProperty("audio_url", audioUrl);
        body.add("speech_models", speechModels);
        body.addProperty("sentiment_analysis", true);
        body.addProperty("speaker_labels", true); // adds a speaker to each sentiment result

        JsonObject submitted = send(HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v2/transcript"))
                .header("Authorization", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build());
        String transcriptId = submitted.get("id").getAsString();

        JsonObject transcript;
        while (true) {
            transcript = send(HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v2/transcript/" + transcriptId))
                    .header("Authorization", apiKey)
                    .GET()
                    .build());
            String status = transcript.get("status").getAsString();
            if (status.equals("completed")) break;
            if (status.equals("error")) {
                throw new RuntimeException("Transcription failed: " + transcript.get("error").getAsString());
            }
            Thread.sleep(3000);
        }

        // One result per sentence: POSITIVE, NEUTRAL, or NEGATIVE, with a confidence score
        for (JsonElement element : transcript.getAsJsonArray("sentiment_analysis_results")) {
            JsonObject result = element.getAsJsonObject();
            JsonElement speaker = result.get("speaker");
            System.out.printf("[%d-%d ms] Speaker %s | %s (%.2f)%n  %s%n",
                    result.get("start").getAsLong(),
                    result.get("end").getAsLong(),
                    speaker == null || speaker.isJsonNull() ? "?" : speaker.getAsString(),
                    result.get("sentiment").getAsString(),
                    result.get("confidence").getAsDouble(),
                    result.get("text").getAsString());
        }
    }

    private static JsonObject send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
