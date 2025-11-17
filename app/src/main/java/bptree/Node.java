package bptree;

/**
 * Represents a single B+Tree node stored in a fixed-size page.
 * <p>
 * A node may be either an internal node or a leaf node:
 * <ul>
 *     <li>Leaf nodes store key/value pairs and form a linked list via {@code next}.</li>
 *     <li>Internal nodes store keys and child page references.</li>
 * </ul>
 * Arrays are allocated using {@link #initArrays(int)} based on the tree's order.
 */
public class Node {

    /** Whether this node is a leaf node. */
    final boolean isLeaf;

    /** Page ID of this node in the backing file. */
    final long pageId;

    /** Sorted keys stored in this node (size = order + 1 to allow overflow). */
    long[] keys;

    /** Values stored in leaf nodes (same length as keys). Null for internal nodes. */
    long[] values;

    /** Child page pointers for internal nodes. Length = order + 2 (keys + 1). */
    long[] children;

    /** Number of keys currently stored in this node. */
    int keyCount = 0;

    /** Pointer to the next leaf node (leaf nodes only). -1 if none. */
    long next = -1;

    /** True if the node has been modified and must be flushed to disk. */
    boolean dirty = false;

    /**
     * Creates a new node.
     *
     * @param isLeaf  whether this node is a leaf node
     * @param pageId  the on-disk page identifier for this node
     */
    public Node(boolean isLeaf, long pageId) {
        this.isLeaf = isLeaf;
        this.pageId = pageId;
    }

    /**
     * Allocates the key/value/child arrays based on the tree's order.
     * Arrays are sized one element larger than necessary to support temporary overflow
     * before a split.
     *
     * @param order maximum number of keys allowed before a split
     */
    public void initArrays(int order) {
        keys = new long[order + 1];   // +1 for temporary overflow

        if (isLeaf) {
            values = new long[order + 1];
        } else {
            children = new long[order + 2]; // children = keys + 1 (+1 for overflow)
        }
    }
}
