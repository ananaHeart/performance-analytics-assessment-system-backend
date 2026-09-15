package com.capstone.assessment.v3.mobile.storage;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class V3OriginalScanImageStorageTest {
    @TempDir
    Path temporaryDirectory;
    private Path root;
    private V3OriginalScanImageStorage storage;
    private byte[] jpeg;

    @BeforeEach
    void setUp() throws Exception {
        root = temporaryDirectory.resolve("evidence");
        storage = new V3OriginalScanImageStorage(root.toString());
        BufferedImage image = new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB);
        image.setRGB(4, 5, 0xabcdef);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "jpeg", bytes));
            jpeg = bytes.toByteArray();
        }
    }

    @Test
    void stagesPrivatelyThenPublishesExactOriginalBytesAndMetadata() throws Exception {
        V3OriginalScanImageStorage.OriginalImage image;
        try (var staged = storage.stage(upload(jpeg), hash(jpeg))) {
            image = staged.image();
            assertEquals(4, UUID.fromString(image.attachmentUuid()).version());
            assertEquals(image.attachmentUuid() + ".jpg", image.storageKey());
            assertEquals("local", image.storageProvider());
            assertEquals("image/jpeg", image.mimeType());
            assertEquals(jpeg.length, image.fileSizeBytes());
            assertEquals(hash(jpeg), image.contentHash());
            assertEquals(24, image.width());
            assertEquals(16, image.height());
            assertFalse(Files.exists(root.resolve(image.storageKey())));
            assertError("SCAN_EVIDENCE_NOT_FOUND", () -> storage.read(image));
            assertEquals(image, staged.publish());
            assertEquals(image, staged.publish());
            assertArrayEquals(jpeg, storage.read(image));
            assertStagingEmpty();
        }
        // Closing the staging handle cannot delete a published original.
        assertArrayEquals(jpeg, storage.read(image));
        assertEquals(1, publishedCount());
    }

    @Test
    void abandonRemovesOnlyStagingAndCannotLaterPublish() throws Exception {
        var staged = storage.stage(upload(jpeg), hash(jpeg));
        staged.close();
        staged.close();
        assertStagingEmpty();
        assertEquals(0, publishedCount());
        assertThrows(IllegalStateException.class, staged::publish);
    }

    @Test
    void existingDestinationIsNeverOverwrittenAndUnrelatedEvidenceSurvives() throws Exception {
        byte[] existing = {1, 2, 3};
        Path destination;
        try (var staged = storage.stage(upload(jpeg), hash(jpeg))) {
            destination = root.resolve(staged.image().storageKey());
            Files.write(destination, existing);
            assertError("SCAN_EVIDENCE_STORAGE_FAILED", staged::publish);
            assertArrayEquals(existing, Files.readAllBytes(destination));
        }
        assertArrayEquals(existing, Files.readAllBytes(destination));
        assertStagingEmpty();
    }

    @Test
    void concurrentPublicationOfOneHandleReturnsOneImmutableFile() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (var staged = storage.stage(upload(jpeg), hash(jpeg))) {
            var first = executor.submit(staged::publish);
            var second = executor.submit(staged::publish);
            assertEquals(first.get(), second.get());
            assertEquals(1, publishedCount());
            assertArrayEquals(jpeg, storage.read(staged.image()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void eachStagingCallAllocatesANewIdentityAndDoesNotPretendToDeduplicateRequests() throws Exception {
        try (var first = storage.stage(upload(jpeg), hash(jpeg));
             var second = storage.stage(upload(jpeg), hash(jpeg))) {
            assertNotEquals(first.image().storageKey(), second.image().storageKey());
            assertEquals(first.image().contentHash(), second.image().contentHash());
        }
        assertStagingEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"image/png", "application/octet-stream", "image/*", "not a mime type"})
    void rejectsIncorrectMimeType(String mime) throws Exception {
        var image = new MockMultipartFile("image", "scan.jpg", mime, jpeg);
        assertError("VALIDATION_FAILED", () -> storage.stage(image, hash(jpeg)));
        assertFalse(Files.exists(root));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void rejectsMissingOrNonCanonicalHash(String hash) {
        assertError("VALIDATION_FAILED", () -> storage.stage(upload(jpeg), hash));
        assertFalse(Files.exists(root));
    }

    @Test
    void rejectsHashMismatchAndCleansStaging() throws Exception {
        assertError("IMAGE_HASH_MISMATCH", () -> storage.stage(upload(jpeg), "0".repeat(64)));
        assertStagingEmpty();
        assertEquals(0, publishedCount());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        byte[] empty = {};
        assertError("VALIDATION_FAILED", () -> storage.stage(upload(empty), hash(empty)));
        assertStagingEmpty();
    }

    @Test
    void rejectsPngEvenWithJpegMimeAndMatchingHash() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        byte[] png = bytes.toByteArray();
        assertError("VALIDATION_FAILED", () -> storage.stage(upload(png), hash(png)));
        assertStagingEmpty();
    }

    @Test
    void rejectsTruncatedJpegWithMatchingHash() throws Exception {
        byte[] truncated = Arrays.copyOf(jpeg, jpeg.length - 2);
        assertError("VALIDATION_FAILED", () -> storage.stage(upload(truncated), hash(truncated)));
        assertStagingEmpty();
    }

    @Test
    void rejectsMalformedJpegEvenWithStartAndEndMarkers() throws Exception {
        byte[] malformed = {(byte) 0xff, (byte) 0xd8, 1, 2, 3, (byte) 0xff, (byte) 0xd9};
        assertError("VALIDATION_FAILED", () -> storage.stage(upload(malformed), hash(malformed)));
        assertStagingEmpty();
    }

    @Test
    void rejectsTruncatedPixelDataEvenWhenEndMarkerIsRestored() throws Exception {
        byte[] truncated = Arrays.copyOf(jpeg, jpeg.length - 15);
        truncated[truncated.length - 2] = (byte) 0xff;
        truncated[truncated.length - 1] = (byte) 0xd9;
        assertError("VALIDATION_FAILED", () -> storage.stage(upload(truncated), hash(truncated)));
        assertStagingEmpty();
    }

    @Test
    void rejectsHugeDimensionsBeforePixelAllocation() throws Exception {
        byte[] huge = jpeg.clone();
        int frame = -1;
        for (int i = 0; i < huge.length - 8; i++) {
            if ((huge[i] & 255) == 0xff && (huge[i + 1] & 255) == 0xc0) {
                frame = i;
                break;
            }
        }
        assertTrue(frame > 0, "Generated JPEG must have a baseline frame header");
        // 50000 x 50000 is within JPEG dimension bounds but overflows a signed int pixel count.
        huge[frame + 5] = (byte) 0xc3;
        huge[frame + 6] = (byte) 0x50;
        huge[frame + 7] = (byte) 0xc3;
        huge[frame + 8] = (byte) 0x50;
        V3AuthException error = assertError("VALIDATION_FAILED", () -> storage.stage(upload(huge), hash(huge)));
        assertTrue(error.getMessage().contains("40 million"));
        assertStagingEmpty();
    }

    @Test
    void declaredOversizeIsRejectedBeforeOpeningTheInput() {
        var image = new MockMultipartFile("image", "scan.jpg", "image/jpeg", jpeg) {
            @Override public long getSize() { return V3OriginalScanImageStorage.MAX_FILE_BYTES + 1; }
            @Override public InputStream getInputStream() { throw new AssertionError("Input must not be opened"); }
        };
        V3AuthException error = assertError("PAYLOAD_TOO_LARGE", () -> storage.stage(image, "0".repeat(64)));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
        assertFalse(Files.exists(root));
    }

    @Test
    void actualBytesAreBoundedEvenWhenDeclaredSizeLiesAndInputIsClosed() throws Exception {
        boolean[] closed = {false};
        var image = new MockMultipartFile("image", "scan.jpg", "image/jpeg", jpeg) {
            @Override public long getSize() { return 1; }
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    private long count;
                    @Override public int read() {
                        return count++ <= V3OriginalScanImageStorage.MAX_FILE_BYTES ? 0 : -1;
                    }
                    @Override public int read(byte[] bytes, int offset, int length) {
                        int size = (int) Math.min(length, V3OriginalScanImageStorage.MAX_FILE_BYTES + 1 - count);
                        if (size == 0) return -1;
                        Arrays.fill(bytes, offset, offset + size, (byte) 0);
                        count += size;
                        return size;
                    }
                    @Override public void close() { closed[0] = true; }
                };
            }
        };
        assertError("PAYLOAD_TOO_LARGE", () -> storage.stage(image, "0".repeat(64)));
        assertTrue(closed[0]);
        assertStagingEmpty();
    }

    @Test
    void interruptedInputFailsWithoutPublishingPartialBytes() throws Exception {
        var image = new MockMultipartFile("image", "scan.jpg", "image/jpeg", jpeg) {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    private int count;
                    @Override public int read() throws IOException {
                        if (count++ < 20) return 1;
                        throw new IOException("simulated interrupted transfer");
                    }
                };
            }
        };
        assertError("SCAN_EVIDENCE_STORAGE_FAILED", () -> storage.stage(image, "0".repeat(64)));
        assertStagingEmpty();
        assertEquals(0, publishedCount());
    }

    @Test
    void unwritableStorageShapeFailsWithSafeError() throws Exception {
        Files.writeString(root, "a file cannot contain evidence files");
        var error = assertError("SCAN_EVIDENCE_STORAGE_FAILED", () -> storage.stage(upload(jpeg), hash(jpeg)));
        assertFalse(error.getMessage().contains(root.toString()));
        assertTrue(Files.isRegularFile(root));
    }

    @Test
    void readDetectsModificationOfPublishedBytes() throws Exception {
        try (var staged = storage.stage(upload(jpeg), hash(jpeg))) {
            var image = staged.publish();
            byte[] changed = jpeg.clone();
            changed[30] ^= 1;
            Files.write(root.resolve(image.storageKey()), changed);
            assertError("SCAN_EVIDENCE_INTEGRITY_FAILED", () -> storage.read(image));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../scan.jpg", "..\\scan.jpg", "C:\\scan.jpg", "/tmp/scan.jpg",
            "https://example.test/scan.jpg", "123.jpg", ".staging/scan.part"})
    void readRejectsPathsUrlsAndInvalidKeys(String key) {
        var image = new V3OriginalScanImageStorage.OriginalImage("unused", "local", key,
                "image/jpeg", 1, "0".repeat(64), 1, 1);
        assertError("VALIDATION_FAILED", () -> storage.read(image));
    }

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("image", "../../untrusted-device-path.jpg", "image/jpeg", bytes);
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private V3AuthException assertError(String code, org.junit.jupiter.api.function.Executable action) {
        V3AuthException error = assertThrows(V3AuthException.class, action);
        assertEquals(code, error.getCode());
        return error;
    }

    private void assertStagingEmpty() throws IOException {
        try (var files = Files.list(root.resolve(".staging"))) {
            assertEquals(0, files.count());
        }
    }

    private long publishedCount() throws IOException {
        try (var files = Files.list(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}
