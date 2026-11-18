# AssemblyAI with Java's built-in HttpClient

Four small Java programs that call AssemblyAI with nothing but `java.net.http.HttpClient`, `java.net.http.WebSocket` and the [Gson](https://github.com/google/gson) JSON library. No AssemblyAI SDK.

This is the companion repo for the tutorial [How to use AssemblyAI with Java](https://www.assemblyai.com/blog/how-to-use-assemblyai-with-java).

| File | What it does |
|------|--------------|
| `src/main/java/TranscribeFile.java` | Transcribes a URL or a local file (uploads it first) with AssemblyAI-STT-3.7 (`assemblyai-stt-3-7`, Universal-2 as the fallback for other languages) and polls until it's done |
| `src/main/java/StreamMicrophone.java` | Streams your microphone to the v3 streaming WebSocket with AssemblyAI-STT-3.7-Realtime (`assemblyai-stt-3-7-realtime`) and prints partial and final turns |
| `src/main/java/SummarizeWithLlmGateway.java` | Summarizes a finished transcript through LLM Gateway, passing the transcript by ID |
| `src/main/java/SentimentAnalysis.java` | Transcribes with Sentiment Analysis and speaker labels turned on and prints one sentiment per sentence |
| `pom.xml` | Maven build with the single dependency, Gson 2.14.0 |

## Requirements

- JDK 21 or later (`java -version`)
- An AssemblyAI API key from your [dashboard](https://www.assemblyai.com/dashboard)
- Maven 3.9+ for option A, or nothing extra for option B
- A microphone for `StreamMicrophone`

## Quick start

```bash
git clone https://github.com/kelsey-aai/assemblyai-java-httpclient
cd assemblyai-java-httpclient
export ASSEMBLYAI_API_KEY=<your-key>
```

Every program reads the key from `ASSEMBLYAI_API_KEY` and exits with a message if it isn't set.

### Option A: Maven

```bash
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" TranscribeFile
```

On Windows, use `;` instead of `:` in the classpath.

### Option B: no build tool

Download the Gson jar once, then let Java 21 compile and run each file in one step (single-file source launch):

```bash
mkdir -p lib
curl -L -o lib/gson-2.14.0.jar \
  https://repo1.maven.org/maven2/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar

java -cp lib/gson-2.14.0.jar src/main/java/TranscribeFile.java
```

Or compile everything with `javac`:

```bash
javac -cp lib/gson-2.14.0.jar -d out src/main/java/*.java
java -cp "out:lib/gson-2.14.0.jar" TranscribeFile
```

## Run the examples

The commands below use option B. With Maven, swap `java -cp lib/gson-2.14.0.jar src/main/java/X.java` for `java -cp "target/classes:$(cat cp.txt)" X`.

```bash
# 1. Transcribe the sample file, a public URL, or a local file
java -cp lib/gson-2.14.0.jar src/main/java/TranscribeFile.java
java -cp lib/gson-2.14.0.jar src/main/java/TranscribeFile.java ./meeting.mp3

# 2. Stream your microphone for 30 seconds (or pass a different number of seconds)
java -cp lib/gson-2.14.0.jar src/main/java/StreamMicrophone.java 30

# 3. Summarize a transcript with LLM Gateway (use the ID printed by step 1)
java -cp lib/gson-2.14.0.jar src/main/java/SummarizeWithLlmGateway.java <transcript_id>

# 4. Sentiment Analysis on the sample file or your own URL
java -cp lib/gson-2.14.0.jar src/main/java/SentimentAnalysis.java
```

On macOS, the first run of `StreamMicrophone` asks your terminal for microphone access. If you see only silence, allow it under System Settings > Privacy & Security > Microphone and run it again.

## Notes

- **Streaming billing:** streaming sessions are billed on how long the WebSocket stays open. `StreamMicrophone` always sends `Terminate` and waits for the `Termination` message before it closes.
- **Sentiment Analysis** supports English audio and runs on AssemblyAI-STT-3.7 and Universal-2.
- **LLM Gateway model:** `SummarizeWithLlmGateway` uses `claude-sonnet-4-6`. Swap in any ID from the [available models list](https://www.assemblyai.com/docs/llm-gateway/available-models).
- **Production:** use a [webhook](https://www.assemblyai.com/docs/deployment/webhooks) instead of polling, and keep the API key on your server.

## Docs

- [Pre-recorded speech-to-text](https://www.assemblyai.com/docs/pre-recorded-audio/select-the-speech-model)
- [Streaming message sequence](https://www.assemblyai.com/docs/streaming/message-sequence)
- [LLM Gateway chat completions](https://www.assemblyai.com/docs/llm-gateway/chat-completions)
- [Sentiment Analysis](https://www.assemblyai.com/docs/speech-understanding/sentiment-analysis)

## License

MIT
