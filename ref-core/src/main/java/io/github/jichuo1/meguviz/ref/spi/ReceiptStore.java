package io.github.jichuo1.meguviz.ref.spi;

import java.io.IOException;
import java.util.Optional;

/** Optional portable storage. Implementations must bound keys, records and capacity. */
public interface ReceiptStore {
    Optional<byte[]> read(String key) throws IOException;
    void write(String key, byte[] record) throws IOException;
}
