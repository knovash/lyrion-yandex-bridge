package knovash.saclient.yandex;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Main;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static knovash.saclient.web.SettingsVoice.KeyString.*;

/**
 * Локальный офлайн TTS через piper (нейросетевой, MIT, ru-голоса).
 * Движок по умолчанию с 05.11: без облака, без API-ключей, без оплаты.
 * Бинарник и голосовой модель (.onnx) ставятся на бокс отдельно:
 *   https://github.com/rhasspy/piper  +  https://huggingface.co/rhasspy/piper-voices
 * Пример: echo "текст" | piper --model ru_RU-irina-medium.onnx --output_file out.wav
 */
@Log4j2
public class PiperTTS {

    public static double duration;

    public static String textToVoiceFile(String text) {
        log.info("Piper TTS TEXT: " + text);
        String outputFile = "/home/music/speech_piper.wav";
        try {
            synthesize(text, outputFile);
            new File(outputFile).setReadable(true, false);
            YandexTTS.volumeAmp(outputFile); // опциональное усиление ffmpeg (как у облачных движков)
        } catch (Exception e) {
            log.error("Ошибка синтеза Piper TTS: {}", e.getMessage());
            return null;
        }

        try {
            duration = YandexTTS.getDuration(outputFile);
        } catch (IOException | InterruptedException e) {
            log.error("Не удалось получить длительность: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        log.info("********* DURATION " + duration);

        // 05.11 фикс: отдаём wav с самого клиента (:localPort), порт 8010 не существует
        String httpUrl = "http://" + Main.myIp + ":" + Main.config.localPort + "/music/speech_piper.wav";
        log.info("VOICE:" + httpUrl);
        return httpUrl;
    }

    /**
     * Запуск piper: текст подаём на stdin (UTF-8), wav пишется в outputFile.
     * Ошибки и зависания защищены таймаутом 60 с.
     */
    public static void synthesize(String text, String outputFile) throws IOException, InterruptedException {
        String bin = VOICE_STRING_PIPER_BIN.value;
        String voice = VOICE_STRING_PIPER_VOICE.value;
        if (bin == null || bin.isEmpty() || "---".equals(bin)) {
            throw new IOException("piper не задан: Настройки голоса -> Piper: команда/путь");
        }
        if (voice == null || voice.isEmpty()) {
            throw new IOException("голос piper не задан: Настройки голоса -> Piper: путь к .onnx");
        }
        log.info("PIPER: " + bin + " --model " + voice + " -> " + outputFile);

        ProcessBuilder pb = new ProcessBuilder(bin, "--model", voice, "--output_file", outputFile);
        pb.redirectErrorStream(true); // stdout+stderr вместе — читаем всё, чтобы не зависнуть на полном буфере
        Process process = pb.start();

        try (OutputStream os = process.getOutputStream()) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }

        StringBuilder output = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("piper: таймаут 60 с");
        }
        File out = new File(outputFile);
        if (process.exitValue() != 0 || !out.exists() || out.length() == 0) {
            throw new IOException("piper завершился с кодом " + process.exitValue() + ". Вывод:\n" + output);
        }
    }
}