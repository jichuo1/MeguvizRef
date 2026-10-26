package io.github.jichuo1.meguviz.ref.diagnostics;

import io.github.jichuo1.meguviz.ref.spi.ReceiptStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MemoryReceiptStore implements ReceiptStore {
    private final int capacity;
    private final Map<String, byte[]> records = new LinkedHashMap<>(16, 0.75f, true);
    public MemoryReceiptStore(int capacity) {
        if (capacity < 1 || capacity > 10000) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    @Override public synchronized Optional<byte[]> read(String key) {
        validateKey(key);
        byte[] bytes = records.get(key);
        return bytes == null ? Optional.empty() : Optional.of(bytes.clone());
    }
    @Override public synchronized void write(String key, byte[] record) {
        validateKey(key);
        if (record.length > BoundedJson.MAX_BYTES) throw new IllegalArgumentException("record too large");
        records.put(key, record.clone());
        while (records.size() > capacity) records.remove(records.keySet().iterator().next());
    }
    static void validateKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 4096) throw new IllegalArgumentException("key");
    }
}
