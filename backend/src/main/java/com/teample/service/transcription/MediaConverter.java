package com.teample.service.transcription;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ffmpeg가 설치된 서버에서만 동작하는 선택적 변환기.
 * STT 제공자가 받지 못하는 형식(webm, mov, mkv 등)을 16kHz 모노 wav로 바꾼다.
 */
@Service
public class MediaConverter {

    private final String ffmpegPath;
    private final String ffprobePath;
    private volatile Boolean available;

    public MediaConverter(
            @Value("${app.media.ffmpeg:ffmpeg}") String ffmpegPath,
            @Value("${app.media.ffprobe:ffprobe}") String ffprobePath
    ) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
    }

    public boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        boolean result;
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            result = process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            result = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = false;
        }
        available = result;
        return result;
    }

    /** 입력 파일에서 음성만 뽑아 wav로 저장하고 그 경로를 돌려준다. */
    public Path extractAudio(Path input) throws IOException {
        if (!isAvailable()) {
            throw new IOException("ffmpeg를 사용할 수 없습니다.");
        }
        Path output = input.resolveSibling(stripExtension(input.getFileName().toString()) + ".converted.wav");
        List<String> command = List.of(
                ffmpegPath, "-y", "-i", input.toString(),
                "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le",
                output.toString()
        );
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] log = process.getInputStream().readAllBytes();
        boolean finished;
        try {
            finished = process.waitFor(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("ffmpeg 변환이 중단되었습니다.");
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffmpeg 변환 시간이 초과되었습니다.");
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
            String tail = new String(log, StandardCharsets.UTF_8);
            throw new IOException("ffmpeg 변환 실패: " + (tail.length() > 300 ? tail.substring(tail.length() - 300) : tail));
        }
        return output;
    }

    /** ffprobe로 길이(ms)를 읽는다. 실패하면 null. */
    public Long probeDurationMs(Path input) {
        try {
            Process process = new ProcessBuilder(
                    ffprobePath, "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", input.toString()
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0 || output.isEmpty()) {
                return null;
            }
            return Math.round(Double.parseDouble(output) * 1000);
        } catch (IOException | NumberFormatException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
