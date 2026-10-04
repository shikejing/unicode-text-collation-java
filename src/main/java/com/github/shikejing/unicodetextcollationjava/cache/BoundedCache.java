package com.github.shikejing.unicodetextcollationjava.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Bounded, thread-safe LRU cache.
 * <p>
 * The cache holds at most {@code maxSize} entries; when the limit is exceeded the least
 * recently used entry is evicted. All operations are safe for concurrent use. Stored
 * values are expected to be immutable, so hit and miss paths always return consistent
 * results.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class BoundedCache<K, V> {

    private final Map<K, V> map;
    private final int maxSize;

    /**
     * Creates a cache with the default maximum size of 1024 entries.
     */
    public BoundedCache() {
        this(1024);
    }

    /**
     * Creates a cache with the given maximum size.
     *
     * @param maxSize maximum number of entries; must be positive
     */
    public BoundedCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive: " + maxSize);
        }
        this.maxSize = maxSize;
        this.map = Collections.synchronizedMap(new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > BoundedCache.this.maxSize;
            }
        });
    }

    /**
     * Returns the cached value for the key, or {@code null} if absent.
     */
    public V get(K key) {
        return map.get(key);
    }

    /**
     * Stores a value for the key.
     */
    public void put(K key, V value) {
        map.put(key, value);
    }

    /**
     * Returns the cached value for the key, computing it with {@code loader} if absent.
     * <p>
     * The loader may be invoked by multiple threads for the same key concurrently; this
     * is safe because the loader must be deterministic and its result immutable. The
     * last computed result wins, but all results are equal.
     */
    public V getOrCompute(K key, Function<? super K, ? extends V> loader) {
        V existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        V computed = loader.apply(key);
        if (computed != null) {
            map.put(key, computed);
        }
        return computed;
    }

    /**
     * Returns the current number of cached entries.
     */
    public int size() {
        return map.size();
    }

    /**
     * Removes all cached entries.
     */
    public void clear() {
        map.clear();
    }
}
