package cloud.kitelang.provider.terraform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for {@link SchemaVersionEnvelope}: the persistence format that
 * records the Terraform resource type schema version alongside the provider's
 * private bytes inside the engine-persisted private state
 * (kitecorp/kite-providers#5).
 */
class SchemaVersionEnvelopeTest {

    private static final byte[] PROVIDER_BYTES = "{\"timeouts\":null}".getBytes(StandardCharsets.UTF_8);

    @Nested
    @DisplayName("wrap()")
    class Wrap {

        @Test
        @DisplayName("should prefix provider bytes with the magic marker and big-endian version")
        void shouldPrefixWithMagicAndVersion() {
            var wrapped = SchemaVersionEnvelope.wrap(3, PROVIDER_BYTES);

            // 6-byte magic + 8-byte big-endian long + payload
            assertEquals(6 + 8 + PROVIDER_BYTES.length, wrapped.length);
            assertArrayEquals("ktfsv1".getBytes(StandardCharsets.US_ASCII),
                    java.util.Arrays.copyOfRange(wrapped, 0, 6));
            assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 3},
                    java.util.Arrays.copyOfRange(wrapped, 6, 14));
            assertArrayEquals(PROVIDER_BYTES, java.util.Arrays.copyOfRange(wrapped, 14, wrapped.length));
        }

        @Test
        @DisplayName("should wrap empty provider bytes into a bare version marker")
        void shouldWrapEmptyProviderBytes() {
            var wrapped = SchemaVersionEnvelope.wrap(0, new byte[0]);

            assertEquals(14, wrapped.length);
            assertEquals(0, SchemaVersionEnvelope.unwrap(wrapped).schemaVersion());
            assertArrayEquals(new byte[0], SchemaVersionEnvelope.unwrap(wrapped).providerBytes());
        }

        @Test
        @DisplayName("should treat null provider bytes as empty")
        void shouldTreatNullProviderBytesAsEmpty() {
            var wrapped = SchemaVersionEnvelope.wrap(1, null);

            assertArrayEquals(new byte[0], SchemaVersionEnvelope.unwrap(wrapped).providerBytes());
        }

        @Test
        @DisplayName("should reject negative schema versions")
        void shouldRejectNegativeVersions() {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> SchemaVersionEnvelope.wrap(-1, PROVIDER_BYTES));
            assertEquals("Schema version must be non-negative, got -1", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("unwrap()")
    class Unwrap {

        @Test
        @DisplayName("should round-trip version and provider bytes")
        void shouldRoundTrip() {
            var unwrapped = SchemaVersionEnvelope.unwrap(SchemaVersionEnvelope.wrap(7, PROVIDER_BYTES));

            assertEquals(7, unwrapped.schemaVersion());
            assertArrayEquals(PROVIDER_BYTES, unwrapped.providerBytes());
        }

        @Test
        @DisplayName("should pass legacy unwrapped bytes through with UNKNOWN_VERSION")
        void shouldPassLegacyBytesThrough() {
            // State persisted before kitecorp/kite-providers#5 carries the raw
            // provider bytes with no envelope; its write-time schema version is
            // unknowable, so no upgrade may be attempted for it.
            var unwrapped = SchemaVersionEnvelope.unwrap(PROVIDER_BYTES);

            assertEquals(SchemaVersionEnvelope.UNKNOWN_VERSION, unwrapped.schemaVersion());
            assertArrayEquals(PROVIDER_BYTES, unwrapped.providerBytes());
        }

        @Test
        @DisplayName("should treat null and empty stored bytes as legacy empty")
        void shouldTreatNullAndEmptyAsLegacyEmpty() {
            assertEquals(SchemaVersionEnvelope.UNKNOWN_VERSION,
                    SchemaVersionEnvelope.unwrap(null).schemaVersion());
            assertArrayEquals(new byte[0], SchemaVersionEnvelope.unwrap(null).providerBytes());

            assertEquals(SchemaVersionEnvelope.UNKNOWN_VERSION,
                    SchemaVersionEnvelope.unwrap(new byte[0]).schemaVersion());
            assertArrayEquals(new byte[0], SchemaVersionEnvelope.unwrap(new byte[0]).providerBytes());
        }

        @Test
        @DisplayName("should treat bytes shorter than a full envelope as legacy")
        void shouldTreatTruncatedBytesAsLegacy() {
            // "ktfsv1" alone could never be produced by wrap(); a provider whose
            // own private bytes happen to be that short must not lose them
            var truncated = "ktfsv1".getBytes(StandardCharsets.US_ASCII);

            var unwrapped = SchemaVersionEnvelope.unwrap(truncated);

            assertEquals(SchemaVersionEnvelope.UNKNOWN_VERSION, unwrapped.schemaVersion());
            assertArrayEquals(truncated, unwrapped.providerBytes());
        }
    }
}
