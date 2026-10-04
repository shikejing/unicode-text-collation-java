package com.github.shikejing.unicodetextcollationjava.collate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * A versioned, self-describing, persistable sort key.
 * <p>
 * Binary layout (big-endian):
 * <pre>
 *   offset 0  : magic {@code "UCOL"} (4 bytes)
 *   offset 4  : key format version (1 byte)
 *   offset 5  : configuration identity hash (4 bytes)
 *   offset 9  : locale tag length (2 bytes) + locale tag (UTF-8)
 *   then      : strength ordinal (1 byte)
 *   then      : version tag length (2 bytes) + version tag (UTF-8)
 *   then      : raw collation key length (2 bytes) + raw collation key bytes
 * </pre>
 * <p>
 * The framing makes keys self-describing: {@link #fromBytes(byte[])} /
 * {@link #fromBase64(String)} reconstruct the full {@link CollationConfig}, and a
 * version mismatch or corrupted frame is reported explicitly instead of silently
 * producing a wrong ordering.
 * <p>
 * Keys are only comparable when they carry the same configuration; comparing keys
 * from different configurations throws {@link IllegalArgumentException}.
 */
public final class SortKey implements Comparable<SortKey> {

    /** Current key framing format version. Bump when the binary layout changes. */
    public static final int FORMAT_VERSION = 1;

    private static final byte[] MAGIC = "UCOL".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_FIXED = 4 + 1 + 4 + 2; // magic + version + configHash + localeLen
    private static final int MAX_LOCALE_BYTES = 0xFFFF;
    private static final int MAX_VERSION_BYTES = 0xFFFF;
    private static final int MAX_KEY_BYTES = 0xFFFF;

    private final CollationConfig config;
    private final byte[] keyBytes;

    SortKey(CollationConfig config, byte[] keyBytes) {
        this.config = config;
        this.keyBytes = keyBytes.clone();
    }

    /**
     * Reconstructs a sort key from its framed binary representation.
     *
     * @throws IllegalArgumentException if the frame is corrupted or uses an unsupported
     *                                  format version
     */
    public static SortKey fromBytes(byte[] framed) {
        if (framed == null || framed.length < HEADER_FIXED + 1 + 2 + 2) {
            throw new IllegalArgumentException("corrupted sort key: frame too short");
        }
        int pos = 0;
        for (byte b : MAGIC) {
            if (framed[pos++] != b) {
                throw new IllegalArgumentException("corrupted sort key: bad magic");
            }
        }
        int formatVersion = framed[pos++] & 0xFF;
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported sort key format version: " + formatVersion
                            + " (this service supports version " + FORMAT_VERSION + ")");
        }
        int configHash = readInt(framed, pos);
        pos += 4;

        int localeLen = readShort(framed, pos);
        pos += 2;
        checkRange(localeLen, MAX_LOCALE_BYTES, "locale");
        checkBounds(framed, pos, localeLen);
        String localeTag = new String(framed, pos, localeLen, StandardCharsets.UTF_8);
        pos += localeLen;

        int strengthOrdinal = framed[pos++] & 0xFF;
        CollationStrength[] strengths = CollationStrength.values();
        if (strengthOrdinal >= strengths.length) {
            throw new IllegalArgumentException("corrupted sort key: unknown strength ordinal");
        }
        CollationStrength strength = strengths[strengthOrdinal];

        int versionLen = readShort(framed, pos);
        pos += 2;
        checkRange(versionLen, MAX_VERSION_BYTES, "version tag");
        checkBounds(framed, pos, versionLen);
        String versionTag = new String(framed, pos, versionLen, StandardCharsets.UTF_8);
        pos += versionLen;

        int keyLen = readShort(framed, pos);
        pos += 2;
        checkRange(keyLen, MAX_KEY_BYTES, "key");
        checkBounds(framed, pos, keyLen);
        if (framed.length != pos + keyLen) {
            throw new IllegalArgumentException("corrupted sort key: length mismatch");
        }
        byte[] keyBytes = Arrays.copyOfRange(framed, pos, pos + keyLen);

        CollationConfig config = new CollationConfig(localeTag, strength, versionTag);
        if (config.identityHash() != configHash) {
            throw new IllegalArgumentException("corrupted sort key: configuration identity hash mismatch");
        }
        return new SortKey(config, keyBytes);
    }

    /**
     * Reconstructs a sort key from its base64 representation.
     */
    public static SortKey fromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            throw new IllegalArgumentException("empty sort key base64 string");
        }
        return fromBytes(Base64.getDecoder().decode(base64));
    }

    /**
     * Returns the framed binary representation (a copy).
     */
    public byte[] bytes() {
        byte[] localeBytes = config.localeTag().getBytes(StandardCharsets.UTF_8);
        byte[] versionBytes = config.versionTag().getBytes(StandardCharsets.UTF_8);
        if (localeBytes.length > MAX_LOCALE_BYTES || versionBytes.length > MAX_VERSION_BYTES
                || keyBytes.length > MAX_KEY_BYTES) {
            throw new IllegalStateException("sort key exceeds maximum frame size");
        }
        byte[] frame = new byte[HEADER_FIXED + localeBytes.length + 1 + 2 + versionBytes.length + 2
                + keyBytes.length];
        int pos = 0;
        System.arraycopy(MAGIC, 0, frame, pos, MAGIC.length);
        pos += MAGIC.length;
        frame[pos++] = (byte) FORMAT_VERSION;
        writeInt(frame, pos, config.identityHash());
        pos += 4;
        writeShort(frame, pos, localeBytes.length);
        pos += 2;
        System.arraycopy(localeBytes, 0, frame, pos, localeBytes.length);
        pos += localeBytes.length;
        frame[pos++] = (byte) config.strength().ordinal();
        writeShort(frame, pos, versionBytes.length);
        pos += 2;
        System.arraycopy(versionBytes, 0, frame, pos, versionBytes.length);
        pos += versionBytes.length;
        writeShort(frame, pos, keyBytes.length);
        pos += 2;
        System.arraycopy(keyBytes, 0, frame, pos, keyBytes.length);
        return frame;
    }

    /**
     * Returns the framed representation as a base64 string suitable for persistence.
     */
    public String toBase64() {
        return Base64.getEncoder().encodeToString(bytes());
    }

    /**
     * Returns the raw (unframed) ICU collation key bytes (a copy).
     */
    public byte[] rawKeyBytes() {
        return keyBytes.clone();
    }

    /**
     * Returns the configuration this key was generated with.
     */
    public CollationConfig config() {
        return config;
    }

    @Override
    public int compareTo(SortKey other) {
        if (!this.config.equals(other.config)) {
            throw new IllegalArgumentException(
                    "sort keys generated with different collation configurations are not comparable: "
                            + this.config + " vs " + other.config);
        }
        return compareUnsigned(this.keyBytes, other.keyBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SortKey other)) {
            return false;
        }
        return config.equals(other.config) && Arrays.equals(keyBytes, other.keyBytes);
    }

    @Override
    public int hashCode() {
        return config.hashCode() * 31 + Arrays.hashCode(keyBytes);
    }

    @Override
    public String toString() {
        return "SortKey[config=" + config + ", keyBytes=" + keyBytes.length + "]";
    }

    private static int compareUnsigned(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = a[i] & 0xFF;
            int y = b[i] & 0xFF;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private static int readInt(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
    }

    private static void writeInt(byte[] data, int pos, int value) {
        data[pos] = (byte) (value >>> 24);
        data[pos + 1] = (byte) (value >>> 16);
        data[pos + 2] = (byte) (value >>> 8);
        data[pos + 3] = (byte) value;
    }

    private static int readShort(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
    }

    private static void writeShort(byte[] data, int pos, int value) {
        data[pos] = (byte) (value >>> 8);
        data[pos + 1] = (byte) value;
    }

    private static void checkRange(int value, int max, String name) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("corrupted sort key: " + name + " length out of range");
        }
    }

    private static void checkBounds(byte[] data, int pos, int length) {
        if (pos < 0 || length < 0 || pos + length > data.length) {
            throw new IllegalArgumentException("corrupted sort key: frame truncated");
        }
    }
}
