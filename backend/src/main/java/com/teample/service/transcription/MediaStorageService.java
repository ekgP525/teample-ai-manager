package com.teample.service.transcription;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 업로드된 음성·영상 파일을 서버 로컬 디스크에 보관한다.
 * 경로는 app.media.dir 기준 상대 경로로 DB에 저장하고, 여기서만 절대 경로로 풀어 쓴다.
 */
@Service
public class MediaStorageService {

    private final Path baseDir;

    public MediaStorageService(@Value("${app.media.dir:./data/media}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    public Path baseDir() {
        return baseDir;
    }

    /** 스트림을 저장하고 상대 경로(예: 2026-10/uuid.mp3)를 돌려준다. */
    public String store(InputStream input, String extension) throws IOException {
        String safeExtension = extension == null ? "" : extension.toLowerCase().replaceAll("[^a-z0-9]", "");
        String folder = LocalDate.now().toString().substring(0, 7);
        String fileName = UUID.randomUUID() + (safeExtension.isEmpty() ? "" : "." + safeExtension);
        Path target = resolve(folder + "/" + fileName);
        Files.createDirectories(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        return folder + "/" + fileName;
    }

    /** 상대 경로를 절대 경로로 바꾼다. 저장소 밖을 가리키면 거부한다. */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("저장 경로가 비어 있습니다.");
        }
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("허용되지 않은 저장 경로입니다.");
        }
        return resolved;
    }

    public boolean exists(String relativePath) {
        try {
            return relativePath != null && Files.isRegularFile(resolve(relativePath));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | IllegalArgumentException ignored) {
            // 파일이 이미 없거나 경로가 잘못된 경우는 무시한다.
        }
    }
}
