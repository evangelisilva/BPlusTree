package bptree;

import java.util.LinkedHashMap;
import java.util.function.Consumer;

/**
 * A simple, thread-safe Least Recently Used (LRU) cache used to store
 * B+Tree nodes in memory. When the cache reaches its capacity, the least
 * recently accessed entry is evicted. If that entry is marked dirty,
 * the configured eviction handler is invoked (typically writing the node
 * back to disk).
 *
 * <p>This implementation uses {@link LinkedHashMap} in access-order mode
 * to efficiently track which entry is the least recently used.</p>
 *
 * @param <K> key type (usually page ID)
 * @param <V> value type, must extend {@link Node}
 */
public class LRUCache<K, V extends Node> {

    /** Maximum number of entries the cache may hold. */
    private final int capacity;

    /**
     * Underlying map storing cached entries.
     * The third constructor argument {@code true} enables access-order tracking,
     * allowing efficient LRU eviction.
     */
    private final LinkedHashMap<K, V> map;

    /**
     * Callback invoked when a dirty node is evicted from the cache.
     * Typically used to flush the node to disk.
     */
    private final Consumer<V> onEvict;

    /**
     * Number of cache hits.
     * <p>
     * A hit occurs when a requested page ID is already present in the cache
     * and retrieval requires no disk access.
     */
    private long hits = 0;

    /**
     * Number of cache misses.
     * <p>
     * A miss occurs when a requested page ID is not present in the cache,
     * forcing the caller to load the node from disk.
     */
    private long misses = 0;

    /**
     * Number of cache evictions performed by the LRU policy.
     * <p>
     * An eviction occurs when inserting a new entry causes the cache to exceed
     * capacity. The least-recently-used entry is removed, and if it is dirty,
     * the eviction callback is used to flush it to disk.
     */
    private long evictions = 0;

    /**
     * Creates a new LRU cache.
     *
     * @param capacity maximum number of entries to hold before evicting
     * @param onEvict  handler called when a dirty node is evicted
     */
    public LRUCache(int capacity, Consumer<V> onEvict) {
        this.capacity = capacity;
        this.onEvict = onEvict;

        // accessOrder = true enables LRU behavior
        this.map = new LinkedHashMap<>(capacity, 0.75f, true);
    }

    /**
     * Retrieves a value by key, updating its recency ordering.
     *
     * @param key lookup key
     * @return cached value or null if absent
     */
    public synchronized V get(K key) {
        V val = map.get(key);
        if (val != null) {
            hits++;
        } else {
            misses++;
        }
        return val;
    }

    /**
     * Inserts or updates an entry, performing LRU eviction if necessary.
     * If the evicted node is dirty, the eviction handler is invoked.
     *
     * @param key  key to store
     * @param node value to store (a B+Tree node)
     */
    public synchronized void put(K key, V node) {
        // Evict LRU entry if needed
        if (map.size() >= capacity && !map.containsKey(key)) {
        // if (map.size() >= capacity) {
            // Evict least-recently-used entry
            K oldestKey = map.keySet().iterator().next();
            V oldest = map.remove(oldestKey);

            if (oldest != null) {
                evictions++;

                // flush dirty nodes
                if (oldest.dirty) {
                    onEvict.accept(oldest);
                }
            }
        }
        map.put(key, node);
    }

    /**
     * Flushes all dirty nodes by invoking the eviction handler,
     * then clears the cache entirely.
     */
    public synchronized void flushAll() {
        for (V node : map.values()) {
            if (node.dirty) {
                onEvict.accept(node);
            }
        }
        map.clear();
    }

    /**
     * Returns the number of cache hits.
     *
     * @return total number of successful lookups performed entirely in-memory
     */
    public synchronized long getHits() {
        return hits;
    }

    /**
     * Returns the number of cache misses.
     *
     * @return total number of lookups that required loading a node from disk
     */
    public synchronized long getMisses() {
        return misses;
    }

    /**
     * Returns the number of evictions performed by the LRU policy.
     *
     * @return total number of entries removed to make room for new cache items
     */
    public synchronized long getEvictions() {
        return evictions;
    }

    /**
     * Computes the current hit rate for the cache.
     *
     * <p>
     * Hit rate is defined as:
     * <pre>
     *     hits / (hits + misses)
     * </pre>
     * This indicates how effective the cache is at avoiding disk I/O.
     *
     * @return hit ratio as a double in [0, 1], or 0 if no accesses have occurred
     */
    public synchronized double getHitRate() {
        long total = hits + misses;
        return (total == 0) ? 0.0 : (double) hits / total;
    }
}
