package com.capstone.assessment.v3.mobile.storage;

import com.capstone.assessment.v3.auth.exception.V3AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Internal storage only: callers must authorize ownership and coordinate database receipts. */
@Service
@Profile("v3")
public class V3OriginalScanImageStorage {
    public static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
    public static final long MAX_PIXELS = 40_000_000L;
    private static final String KEY_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(jpg|png)";
    private static final Logger LOG = LoggerFactory.getLogger(V3OriginalScanImageStorage.class);
    private final Path root;
    private final Path staging;

    public V3OriginalScanImageStorage(
            @Value("${app.v3.scan-evidence.storage-directory:output/v3-scan-evidence}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
        staging = root.resolve(".staging");
    }

    /** Streams and closes the multipart input; never uses the client filename or rewrites pixels. */
    public StagedOriginal stage(MultipartFile image, String expectedHash) {
        if (image == null || !isJpegContentType(image.getContentType())) {
            throw invalid("The original image must have Content-Type image/jpeg.");
        }
        return stageEvidence(image, expectedHash, "image/jpeg");
    }

    /** Shared immutable storage, with PNG permitted only by the separate attachment path. */
    public StagedOriginal stageEvidence(MultipartFile image, String expectedHash, String mimeType) {
        if (image == null || !("image/jpeg".equals(mimeType) || "image/png".equals(mimeType)))
            throw invalid("Evidence must be JPEG or PNG.");
        try {
            if (image.getContentType() == null || !MediaType.parseMediaType(mimeType)
                    .includes(MediaType.parseMediaType(image.getContentType()))) throw invalid("Evidence MIME does not match metadata.");
        } catch (IllegalArgumentException e) { throw invalid("Evidence MIME is invalid."); }
        if (expectedHash == null || !expectedHash.matches("[0-9a-f]{64}")) {
            throw invalid("imageHash must be a lowercase SHA-256 value.");
        }
        if (image.getSize() > MAX_FILE_BYTES) {
            throw tooLarge();
        }
        String attachmentUuid = UUID.randomUUID().toString();
        Path temporary = staging.resolve(attachmentUuid + ".part");
        boolean created = false;
        boolean ready = false;
        try {
            ensureDirectories();
            // CREATE_NEW also prevents following a pre-existing staging symlink.
            Files.createFile(temporary);
            created = true;
            MessageDigest digest = sha256();
            long size;
            try (InputStream input = image.getInputStream();
                 OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.WRITE)) {
                size = copyBounded(input, output, digest);
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            if (!hash.equals(expectedHash)) {
                throw new V3AuthException("IMAGE_HASH_MISMATCH",
                        "imageHash does not match the received original bytes.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            int[] dimensions = "image/jpeg".equals(mimeType) ? validateJpeg(temporary, size) : validatePng(temporary, size);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            OriginalImage descriptor = new OriginalImage(attachmentUuid, "local",
                    attachmentUuid + ("image/jpeg".equals(mimeType) ? ".jpg" : ".png"), mimeType, size, hash, dimensions[0], dimensions[1]);
            ready = true;
            return new StagedOriginal(temporary, descriptor);
        } catch (IOException e) {
            throw storageFailure(e);
        } finally {
            if (created && !ready) {
                discardTemporary(temporary);
            }
        }
    }

    /** Read with the descriptor persisted by the backend, after caller ownership checks. */
    public byte[] read(OriginalImage image) {
        if (image == null) {
            throw invalid("An original image descriptor is required.");
        }
        Path source = resolveKey(image.storageKey());
        try {
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                throw new V3AuthException("SCAN_EVIDENCE_NOT_FOUND", "Original scan evidence is unavailable.",
                        HttpStatus.NOT_FOUND);
            }
            byte[] bytes;
            try (InputStream input = Files.newInputStream(source)) {
                bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES || bytes.length != image.fileSizeBytes()
                    || !HexFormat.of().formatHex(sha256().digest(bytes)).equals(image.contentHash())) {
                throw new V3AuthException("SCAN_EVIDENCE_INTEGRITY_FAILED",
                        "Stored original scan evidence failed its integrity check.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return bytes;
        } catch (IOException e) {
            throw storageFailure(e);
        }
    }

    public record OriginalImage(String attachmentUuid, String storageProvider, String storageKey,
                                String mimeType, long fileSizeBytes, String contentHash, int width, int height) { }

    /** Restore only missing staged bytes for a durable intent, without changing its attachment identity. */
    public void restorePending(OriginalImage expected, StagedOriginal incoming) {
        if (!expected.contentHash().equals(incoming.image().contentHash())
                || expected.fileSizeBytes() != incoming.image().fileSizeBytes()
                || !expected.mimeType().equals(incoming.image().mimeType())
                || expected.width() != incoming.image().width() || expected.height() != incoming.image().height()) {
            throw invalid("Retry bytes do not match the durable original image.");
        }
        Path published = resolveKey(expected.storageKey());
        Path retained = recoveryPath(expected);
        try {
            if (Files.exists(published, LinkOption.NOFOLLOW_LINKS)) { read(expected); return; }
            if (!Files.exists(retained, LinkOption.NOFOLLOW_LINKS)) {
                try { Files.createLink(retained, incoming.temporary); }
                catch (FileAlreadyExistsException ignored) { /* Verify the winning file below. */ }
            }
            verifyStaged(expected, retained);
        } catch (IOException e) { throw storageFailure(e); }
    }

    /** Idempotent publication from a DB-owned intent, usable after process restart. Caller holds its ledger row lock. */
    public OriginalImage recoverPublish(OriginalImage expected) {
        Path published = resolveKey(expected.storageKey());
        Path retained = recoveryPath(expected);
        try {
            if (!Files.exists(published, LinkOption.NOFOLLOW_LINKS)) {
                verifyStaged(expected, retained);
                try { Files.createLink(published, retained); }
                catch (FileAlreadyExistsException ignored) { /* Never replace: verify the existing object. */ }
            }
            read(expected);
            discardTemporary(retained);
            return expected;
        } catch (IOException | UnsupportedOperationException e) { throw storageFailure(e); }
    }

    private Path recoveryPath(OriginalImage image) {
        resolveKey(image.storageKey());
        String extension = "image/jpeg".equals(image.mimeType()) ? ".jpg" : "image/png".equals(image.mimeType()) ? ".png" : null;
        if (extension == null || !image.storageKey().equals(image.attachmentUuid() + extension)) throw invalid("Invalid evidence identity.");
        return staging.resolve(image.attachmentUuid() + ".part");
    }

    private void verifyStaged(OriginalImage image, Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new V3AuthException("SCAN_EVIDENCE_NOT_FOUND", "Original bytes must be retried from the device.", HttpStatus.NOT_FOUND);
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) { bytes = input.readNBytes((int) MAX_FILE_BYTES + 1); }
        if (bytes.length != image.fileSizeBytes() || bytes.length > MAX_FILE_BYTES
                || !HexFormat.of().formatHex(sha256().digest(bytes)).equals(image.contentHash())) {
            throw new V3AuthException("SCAN_EVIDENCE_INTEGRITY_FAILED", "Retained original bytes failed verification.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Close abandons only staging; published evidence has no overwrite or deletion API. */
    public final class StagedOriginal implements AutoCloseable {
        private final Path temporary;
        private final OriginalImage image;
        private boolean published;
        private boolean closed;
        private boolean retainedForRecovery;

        private StagedOriginal(Path temporary, OriginalImage image) {
            this.temporary = temporary;
            this.image = image;
        }

        /** Stable storage identity is available before publication for a future recovery ledger. */
        public OriginalImage image() {
            return image;
        }

        /** Call before committing the durable intent; an uncertain commit must not discard its bytes. */
        public synchronized void retainForRecovery() {
            if (closed) throw new IllegalStateException("The staging handle is closed.");
            retainedForRecovery = true;
        }

        public synchronized OriginalImage publish() {
            if (closed) {
                throw new IllegalStateException("The staging handle is closed.");
            }
            if (!published) {
                try {
                    // Same-volume hard link atomically publishes complete bytes and fails if the key exists.
                    // ATOMIC_MOVE is deliberately avoided: it can replace an existing target on some systems.
                    Files.createLink(resolveKey(image.storageKey()), temporary);
                    published = true;
                } catch (IOException | UnsupportedOperationException e) {
                    throw storageFailure(e);
                }
                discardTemporary(temporary);
            }
            return image;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                if (!retainedForRecovery) discardTemporary(temporary);
                closed = true;
            }
        }
    }

    private void ensureDirectories() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(staging);
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(staging)) {
            throw new IOException("Evidence directories must not be symbolic links.");
        }
    }

    private Path resolveKey(String key) {
        if (key == null || !key.matches(KEY_PATTERN)) {
            throw invalid("Invalid original scan storage key.");
        }
        return root.resolve(key);
    }

    private static long copyBounded(InputStream input, OutputStream output, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[8192];
        long size = 0;
        int count;
        while ((count = input.read(buffer, 0, (int) Math.min(buffer.length, MAX_FILE_BYTES - size + 1))) != -1) {
            size += count;
            if (size > MAX_FILE_BYTES) {
                throw tooLarge();
            }
            output.write(buffer, 0, count);
            digest.update(buffer, 0, count);
        }
        return size;
    }

    private static int[] validateJpeg(Path file, long size) {
        ImageReader reader = ImageIO.getImageReadersByFormatName("JPEG").next();
        boolean[] warned = {false};
        reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
        try (FileImageInputStream input = new FileImageInputStream(file.toFile())) {
            if (size < 4 || input.readUnsignedShort() != 0xffd8) {
                throw invalid("Original image bytes must be a complete JPEG.");
            }
            input.seek(size - 2);
            if (input.readUnsignedShort() != 0xffd9) {
                throw invalid("The original JPEG must end with its end-of-image marker.");
            }
            input.seek(0);
            reader.setInput(input);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                throw invalid("The original JPEG must contain at most 40 million pixels.");
            }
            BufferedImage decoded = reader.read(0);
            try {
                if (decoded == null || warned[0]) {
                    throw invalid("The original JPEG is malformed or incomplete.");
                }
                return new int[]{width, height};
            } finally {
                if (decoded != null) {
                    decoded.flush();
                }
            }
        } catch (IOException e) {
            throw invalid("The original JPEG cannot be fully decoded.");
        } finally {
            reader.dispose();
        }
    }

    private static int[] validatePng(Path file, long size) {
        ImageReader reader = ImageIO.getImageReadersByFormatName("PNG").next();
        boolean[] warned = {false};
        reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
        try (FileImageInputStream input = new FileImageInputStream(file.toFile())) {
            if (size < 45 || input.readLong() != 0x89504e470d0a1a0aL) throw invalid("Evidence bytes must be a complete PNG.");
            // Verify every chunk CRC and require a terminal IEND; ImageIO alone tolerates damaged CRCs.
            boolean ended = false;
            while (input.getStreamPosition() < size) {
                long length = Integer.toUnsignedLong(input.readInt());
                int type = input.readInt();
                if (length > MAX_FILE_BYTES || length + 4 > size - input.getStreamPosition()) throw invalid("Malformed PNG chunk.");
                var crc = new java.util.zip.CRC32();
                crc.update(new byte[]{(byte)(type >>> 24), (byte)(type >>> 16), (byte)(type >>> 8), (byte)type});
                byte[] buffer = new byte[8192];
                long remaining = length;
                while (remaining > 0) { int count = (int)Math.min(buffer.length, remaining); input.readFully(buffer, 0, count); crc.update(buffer, 0, count); remaining -= count; }
                if (crc.getValue() != Integer.toUnsignedLong(input.readInt())) throw invalid("PNG chunk integrity failed.");
                if (type == 0x49454e44) { ended = length == 0 && input.getStreamPosition() == size; break; }
                if (type == 0x6163544c) throw invalid("Animated PNG evidence is unsupported.");
            }
            if (!ended) throw invalid("PNG must end with IEND.");
            input.seek(0); reader.setInput(input);
            int width = reader.getWidth(0), height = reader.getHeight(0);
            if (width < 1 || height < 1 || (long)width * height > MAX_PIXELS) throw invalid("Evidence exceeds 40 million pixels.");
            BufferedImage decoded = reader.read(0);
            try {
                if (decoded == null || warned[0]) throw invalid("PNG cannot be completely decoded.");
                return new int[]{width, height};
            } finally { if (decoded != null) decoded.flush(); }
        } catch (IOException e) { throw invalid("PNG cannot be completely decoded."); }
        finally { reader.dispose(); }
    }

    private static boolean isJpegContentType(String value) {
        try {
            return value != null && MediaType.IMAGE_JPEG.includes(MediaType.parseMediaType(value));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", e);
        }
    }

    private static V3AuthException invalid(String message) {
        return new V3AuthException("VALIDATION_FAILED", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static V3AuthException tooLarge() {
        return new V3AuthException("PAYLOAD_TOO_LARGE", "The original image exceeds 15 MiB.",
                HttpStatus.PAYLOAD_TOO_LARGE);
    }

    private static V3AuthException storageFailure(Exception cause) {
        V3AuthException failure = new V3AuthException("SCAN_EVIDENCE_STORAGE_FAILED",
                "Original scan evidence could not be stored or read.", HttpStatus.INTERNAL_SERVER_ERROR);
        failure.initCause(cause);
        return failure;
    }

    private static void discardTemporary(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            LOG.warn("Could not remove a staged original scan image; recovery cleanup is required.");
        }
    }
}
