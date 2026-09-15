package com.capstone.assessment.v3.answersheet.service;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Profile("v3")
@Service
public class V3AnswerSheetFileStorage {

    private final Path storageRoot;

    public V3AnswerSheetFileStorage(
            @Value("${app.v3.answer-sheets.storage-directory:output/v3-answer-sheets}") String directory
    ) {
        this.storageRoot = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(String answerSheetUuid, byte[] pdfBytes) {
        String storageKey = answerSheetUuid + ".pdf";
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(storageRoot);
            Path temporary = Files.createTempFile(storageRoot, answerSheetUuid + "-", ".tmp");
            try {
                Files.write(temporary, pdfBytes);
                moveAtomically(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return storageKey;
        } catch (IOException exception) {
            throw new V3AuthException(
                    "ANSWER_SHEET_STORAGE_FAILED",
                    "The generated answer-sheet PDF could not be stored.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public byte[] read(String storageKey) {
        Path path = resolve(storageKey);
        try {
            if (!Files.isRegularFile(path)) {
                throw new V3AuthException(
                        "ANSWER_SHEET_PDF_NOT_FOUND",
                        "The stored answer-sheet PDF is unavailable.",
                        HttpStatus.NOT_FOUND
                );
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new V3AuthException(
                    "ANSWER_SHEET_STORAGE_READ_FAILED",
                    "The stored answer-sheet PDF could not be read.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (RuntimeException | IOException ignored) {
            // Rollback cleanup is best-effort; the transaction remains authoritative.
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new V3AuthException(
                    "ANSWER_SHEET_STORAGE_KEY_INVALID",
                    "The answer-sheet storage key is invalid.",
                    HttpStatus.CONFLICT
            );
        }
        Path relative = Path.of(storageKey);
        if (relative.isAbsolute() || relative.getNameCount() != 1) {
            throw new V3AuthException(
                    "ANSWER_SHEET_STORAGE_KEY_INVALID",
                    "The answer-sheet storage key is invalid.",
                    HttpStatus.CONFLICT
            );
        }
        Path resolved = storageRoot.resolve(relative).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new V3AuthException(
                    "ANSWER_SHEET_STORAGE_KEY_INVALID",
                    "The answer-sheet storage key is invalid.",
                    HttpStatus.CONFLICT
            );
        }
        return resolved;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }
}
