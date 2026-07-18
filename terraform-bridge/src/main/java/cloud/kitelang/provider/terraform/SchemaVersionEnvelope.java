package cloud.kitelang.provider.terraform;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Persistence format recording the Terraform resource type <em>schema version</em>
 * alongside the provider's private bytes (kitecorp/kite-providers#5).
 *
 * <p>Terraform's {@code UpgradeResourceState} keys on the schema version each
 * resource's state was written with; Terraform core stores it per instance in
 * the state file. Kite's engine persists exactly one opaque per-resource blob
 * for the bridge — the provider-private bytes — so the bridge records the
 * version there, as a fixed-size prefix it strips before the bytes ever reach
 * the wrapped provider (SDK-based providers JSON-parse their private bytes and
 * would reject a foreign prefix).</p>
 *
 * <p>Layout: 6-byte ASCII magic {@code ktfsv1}, 8-byte big-endian schema
 * version, then the provider's own bytes verbatim. Bytes without the magic are
 * pre-envelope legacy state: their write-time schema version is unknowable, so
 * {@link #unwrap(byte[])} reports {@link #UNKNOWN_VERSION} and no upgrade may
 * be attempted for them (assuming version 0 could run a provider's 0&rarr;N
 * upgraders against state actually written at version N).</p>
 */
final class SchemaVersionEnvelope {

    /** Schema version of legacy (pre-envelope) private bytes — never upgrade these. */
    static final long UNKNOWN_VERSION = -1;

    private static final byte[] MAGIC = "ktfsv1".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION_BYTES = Long.BYTES;
    private static final int HEADER_LENGTH = MAGIC.length + VERSION_BYTES;

    private SchemaVersionEnvelope() {
    }

    /**
     * Result of {@link #unwrap(byte[])}.
     *
     * @param schemaVersion the recorded schema version, or {@link #UNKNOWN_VERSION}
     *                      for legacy bytes without an envelope
     * @param providerBytes the provider's own private bytes, never null
     */
    record Unwrapped(long schemaVersion, byte[] providerBytes) {
    }

    /**
     * Prefixes the provider's private bytes with the schema version for persistence.
     *
     * @param schemaVersion the resource type's current schema version
     * @param providerBytes the provider's own private bytes; null is treated as empty
     * @return the enveloped bytes to hand to the engine
     */
    static byte[] wrap(long schemaVersion, byte[] providerBytes) {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("Schema version must be non-negative, got " + schemaVersion);
        }
        var payload = providerBytes != null ? providerBytes : new byte[0];
        return ByteBuffer.allocate(HEADER_LENGTH + payload.length)
                .put(MAGIC)
                .putLong(schemaVersion)
                .put(payload)
                .array();
    }

    /**
     * Splits engine-persisted bytes into schema version and provider bytes.
     * Bytes not starting with a full envelope header pass through unchanged
     * with {@link #UNKNOWN_VERSION}, so provider bytes persisted before this
     * format existed are never corrupted or misread.
     *
     * @param persistedBytes the bytes the engine stored, or null when none
     * @return the unwrapped version and provider bytes
     */
    static Unwrapped unwrap(byte[] persistedBytes) {
        if (persistedBytes == null) {
            return new Unwrapped(UNKNOWN_VERSION, new byte[0]);
        }
        if (persistedBytes.length < HEADER_LENGTH || !hasMagic(persistedBytes)) {
            return new Unwrapped(UNKNOWN_VERSION, persistedBytes);
        }
        var buffer = ByteBuffer.wrap(persistedBytes, MAGIC.length, VERSION_BYTES);
        var version = buffer.getLong();
        return new Unwrapped(version,
                Arrays.copyOfRange(persistedBytes, HEADER_LENGTH, persistedBytes.length));
    }

    private static boolean hasMagic(byte[] bytes) {
        return Arrays.equals(bytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }
}
