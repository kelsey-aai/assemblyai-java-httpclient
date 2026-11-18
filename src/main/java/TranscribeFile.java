import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

public class TranscribeFile {
    private static final String BASE_URL = "https://api.assemblyai.com";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("ASSEMBLYAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set the ASSEMBLYAI_API_KEY environment variable first.");
            System.exit(1);
        }

        // Pass a public URL or a local file path. Defaults to a sample file.
        String source = args.length > 0 ? args[0] : "https://assembly.ai/wildfires.mp3";
        String audioUrl = source.startsWith("http://") || source.startsWith("https://")
                ? source
                : upload(apiKey, Path.of(source));

        // Submit the transcription job
        JsonArray speechModels = new JsonArray();
        speechModels.add("assemblyai-stt-3-7");
        speechModels.add("universal-2"); // covers languages AssemblyAI-STT-3.7 doesn't

        JsonObject body = new JsonObject();
        body.addProperty("audio_url", audioUrl);
        body.add("speech_models", speechModels);
        body.addProperty("language_detection", true);

        JsonObject submitted = send(HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v2/transcript"))
                .header("Authorization", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build());
        String transcriptId = submitted.get("id").getAsString();
        System.out.println("Transcript ID: " + transcriptId);

        // Poll until the transcript is ready
        while (true) {
            JsonObject transcript = send(HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v2/transcript/" + transcriptId))
                    .header("Authorization", apiKey)
                    .GET()
                    .build());
            String status = transcript.get("status").getAsString();

            if (status.equals("completed")) {
                System.out.println("Model used: " + transcript.get("speech_model_used").getAsString());
                System.out.println(transcript.get("text").getAsString());
                return;
            }
            if (status.equals("error")) {
                throw new RuntimeException("Transcription failed: " + transcript.get("error").getAsString());
            }
            Thread.sleep(3000);
        }
    }

    // Upload a local file and return the upload_url to transcribe
    private static String upload(String apiKey, Path file) throws Exception {
        JsonObject uploaded = send(HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v2/upload"))
                .header("Authorization", apiKey)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofFile(file))
                .build());
        return uploaded.get("upload_url").getAsString();
    }

    private static JsonObject send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
