import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StreamMicrophone {
    private static final int SAMPLE_RATE = 16000;
    private static final String WS_URL = "wss://streaming.assemblyai.com/v3/ws"
            + "?speech_model=assemblyai-stt-3-7-realtime"
            + "&sample_rate=" + SAMPLE_RATE
            + "&encoding=pcm_s16le";

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("ASSEMBLYAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set the ASSEMBLYAI_API_KEY environment variable first.");
            System.exit(1);
        }
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 30;

        CountDownLatch terminated = new CountDownLatch(1);
        WebSocket ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("Authorization", apiKey)
                .buildAsync(URI.create(WS_URL), new TranscriptListener(terminated))
                .join();

        // 16 kHz, 16-bit, mono, signed, little-endian PCM (pcm_s16le)
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        TargetDataLine mic = AudioSystem.getTargetDataLine(format);
        mic.open(format);
        mic.start();
        System.out.println("Listening for " + seconds + " seconds. Start speaking.");

        byte[] buffer = new byte[SAMPLE_RATE / 20 * 2]; // 50 ms of audio = 1,600 bytes
        long stopAt = System.currentTimeMillis() + seconds * 1000L;
        try {
            while (System.currentTimeMillis() < stopAt && terminated.getCount() > 0) {
                int read = mic.read(buffer, 0, buffer.length);
                if (read > 0) {
                    // Audio goes over as binary frames; send one frame at a time
                    ws.sendBinary(ByteBuffer.wrap(Arrays.copyOf(buffer, read)), true).join();
                }
            }
        } finally {
            mic.stop();
            mic.close();
        }

        // Ask the server to flush the last turn, then wait for the Termination message
        if (terminated.getCount() > 0) {
            ws.sendText("{\"type\": \"Terminate\"}", true).join();
            terminated.await(10, TimeUnit.SECONDS);
        }
        if (!ws.isOutputClosed()) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    static class TranscriptListener implements WebSocket.Listener {
        private final CountDownLatch terminated;
        private final StringBuilder message = new StringBuilder();

        TranscriptListener(CountDownLatch terminated) {
            this.terminated = terminated;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data); // a JSON message can arrive in several parts
            if (last) {
                handle(JsonParser.parseString(message.toString()).getAsJsonObject());
                message.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        private void handle(JsonObject msg) {
            switch (msg.get("type").getAsString()) {
                case "Begin" -> System.out.println("Session started: " + msg.get("id").getAsString()
                        + " (model: " + msg.getAsJsonObject("configuration").get("model").getAsString() + ")");
                case "Turn" -> {
                    String text = msg.get("transcript").getAsString();
                    boolean endOfTurn = msg.get("end_of_turn").getAsBoolean();
                    boolean formatted = msg.get("turn_is_formatted").getAsBoolean();
                    if (endOfTurn && formatted) {
                        System.out.print("\r\033[K");
                        System.out.println(text);
                    } else {
                        // Each partial replaces the previous one for the same turn
                        System.out.print("\r\033[K" + text);
                    }
                }
                case "Termination" -> {
                    System.out.println("\nSession ended. Audio processed: "
                            + msg.get("audio_duration_seconds").getAsString() + " s");
                    terminated.countDown();
                }
                case "Error" -> System.err.println("\nError " + msg.get("error_code").getAsString()
                        + ": " + msg.get("error").getAsString());
                default -> { } // SpeechStarted and other events are ignored here
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (statusCode != WebSocket.NORMAL_CLOSURE) {
                System.err.println("\nConnection closed: " + statusCode + " " + reason);
            }
            terminated.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("\nWebSocket error: " + error.getMessage());
            terminated.countDown();
        }
    }
}
