package io.github.jichuo1.meguviz.ref.diagnostics;

import io.github.jichuo1.meguviz.ref.spi.ReceiptStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** An exclusively-owned store directory. Atomic replacement is required, never silently downgraded. */
public final class FileReceiptStore implements ReceiptStore {
    private final Path directory;
    private final int capacity;
    public FileReceiptStore(Path directory, int capacity) throws IOException {
        if (capacity < 1 || capacity > 10000) throw new IllegalArgumentException("capacity");
        Path normalized = directory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)) throw new IOException("Store directory is a symbolic link");
        Files.createDirectories(normalized);
        this.directory = normalized.toRealPath();
        this.capacity = capacity;
    }
    @Override public synchronized Optional<byte[]> read(String key) throws IOException {
        Path file = path(key);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        checkInterrupted();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size() > BoundedJson.MAX_BYTES) throw new IOException("record too large");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (channel.read(buffer) >= 0) {
                checkInterrupted();
                if (out.size() + buffer.position() > BoundedJson.MAX_BYTES) throw new IOException("record too large");
                out.write(buffer.array(), 0, buffer.position());
                buffer.clear();
            }
            return Optional.of(out.toByteArray());
        }
    }
    @Override public synchronized void write(String key, byte[] record) throws IOException {
        Path target = path(key);
        if (record.length > BoundedJson.MAX_BYTES) throw new IOException("record too large");
        checkInterrupted();
        if (Files.isSymbolicLink(target)) throw new IOException("Symbolic link record rejected");
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            int count = 0;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
                for (Path ignored : files) if (++count >= capacity) throw new IOException("store capacity reached");
            }
        }
        Path temporary = Files.createTempFile(directory, ".receipt-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(record.clone());
                while (bytes.hasRemaining()) { checkInterrupted(); channel.write(bytes); }
                channel.force(true);
            }
            checkInterrupted();
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private Path path(String key) {
        MemoryReceiptStore.validateKey(key);
        return directory.resolve(CodeIdentities.sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".json");
    }
    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("receipt IO interrupted");
    }
}
