package bptree;

import java.io.*;

/**
 * A disk-backed B+Tree supporting inserts and point lookups.
 * <p>
 * Nodes are fixed-size pages managed by {@link DiskManager}, and buffered
 * using an {@link LRUCache}. Each node can contain up to {@code order} keys,
 * where the order is computed from the configured page size.
 *
 * Keys and values are {@code long}. Internal nodes store child page IDs; leaf
 * nodes store actual values. Leaves form a linked list for efficient scans.
 */
public class BPlusTree {

    /** Size of each on-disk page, in bytes. */
    private static final int PAGE_SIZE = 4096;

    private final DiskManager disk;
    private final LRUCache<Long, Node> cache;

    /** Page ID of the current root node. */
    long rootPageId;

    /** Maximum number of keys allowed in a node. */
    private final int order;

    /**
     * Creates or loads a B+Tree from the given file path.
     *
     * @param path            path to the underlying database file
     * @param cacheSizeBytes  size of in-memory cache used to buffer pages
     */
    public BPlusTree(String path, long cacheSizeBytes) throws IOException {

        // Remove any old database file to start fresh
        File f = new File(path);
        if (f.exists()) {
            if (!f.delete()) {
                throw new IOException("Unable to delete existing file: " + path);
            }
        }

        disk = new DiskManager(path, PAGE_SIZE);

        int pages = (int) (cacheSizeBytes / PAGE_SIZE);
        if (pages < 1) pages = 1;

        // LRU cache, flushing evicted nodes back to disk.
        cache = new LRUCache<>(pages, node -> {
            try {
                disk.writeNode(node);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        if (disk.isFresh()) {
            // Create empty tree
            this.order = computeOrder();

            rootPageId = disk.allocatePage();
            Node root = new Node(true, rootPageId);
            root.initArrays(order);

            disk.writeNode(root);
            disk.writeRootPage(rootPageId);

        } else {
            // Load existing root pointer
            this.order = computeOrder();
            this.rootPageId = disk.readRootPage();
        }
    }

    /**
     * Computes the maximum number of keys that can fit in a page.
     */
    private int computeOrder() {
        int entrySize = 16;  // key + value/pointer
        int metadata = 32;   // flags, page header, counters
        return (PAGE_SIZE - metadata) / entrySize;
    }

    /**
     * Inserts the given key/value pair, splitting nodes as required.
     * If the key already exists, its value is updated.
     */
    public void insert(long key, long value) throws IOException {

        Node root = load(rootPageId);
        Split split = insertInternal(root, key, value);

        // Root split: build new root with two children.
        if (split != null) {
            long newRootId = disk.allocatePage();

            Node newRoot = new Node(false, newRootId);
            newRoot.initArrays(order);

            newRoot.keys[0] = split.key;
            newRoot.children[0] = root.pageId;
            newRoot.children[1] = split.newRight.pageId;
            newRoot.keyCount = 1;

            markDirty(newRoot);
            rootPageId = newRootId;
            disk.writeRootPage(newRootId);
        }
    }

    /**
     * Recursive internal insert for a given node.
     *
     * @return a {@link Split} result if the node was split, otherwise {@code null}.
     */
    private Split insertInternal(Node node, long key, long value) throws IOException {

        int pos = binarySearch(node, key);

        if (node.isLeaf) {

            // Key exists → update
            if (pos >= 0) {
                node.values[pos] = value;

            } else {
                // Insert new record
                insertLeafAt(node, -pos - 1, key, value);
            }

        } else {
            // Internal node: descend
            if (pos < 0) pos = -pos - 1;

            Node child = load(node.children[pos]);
            Split split = insertInternal(child, key, value);

            if (split != null) {
                insertInternalAt(node, pos, split.key, split.newRight.pageId);
            }
        }

        // Handle node overflow
        if (node.keyCount > order) {
            return splitNode(node);
        }

        markDirty(node);
        return null;
    }

    /**
     * Searches the tree for the given key.
     *
     * @return the associated value, or {@code null} if the key is absent.
     */
    public Long search(long key) throws IOException {

        Node node = load(rootPageId);

        // Walk down internal nodes
        while (!node.isLeaf) {
            int pos = binarySearch(node, key);
            if (pos < 0) pos = -pos - 1;
            node = load(node.children[pos]);
        }

        // Search within leaf
        int pos = binarySearch(node, key);
        return (pos >= 0) ? node.values[pos] : null;
    }

    /**
     * Binary search for a key inside a node.
     * Returns index if found, else -(insertion point) - 1.
     */
    private int binarySearch(Node node, long key) {
        int lo = 0, hi = node.keyCount - 1;

        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long k = node.keys[mid];

            if (k == key) return mid;
            if (k < key) lo = mid + 1;
            else hi = mid - 1;
        }

        return -(lo + 1);
    }

    /**
     * Inserts a key/value pair into a leaf node at the given position.
     */
    private void insertLeafAt(Node node, int pos, long key, long value) {

        System.arraycopy(node.keys, pos, node.keys, pos + 1, node.keyCount - pos);
        System.arraycopy(node.values, pos, node.values, pos + 1, node.keyCount - pos);

        node.keys[pos] = key;
        node.values[pos] = value;
        node.keyCount++;

        markDirty(node);
    }

    /**
     * Inserts a promoted key and child pointer into an internal node.
     */
    private void insertInternalAt(Node node, int pos, long key, long rightChild) {

        System.arraycopy(node.keys, pos, node.keys, pos + 1, node.keyCount - pos);
        System.arraycopy(node.children, pos + 1, node.children, pos + 2, node.keyCount - pos);

        node.keys[pos] = key;
        node.children[pos + 1] = rightChild;
        node.keyCount++;

        markDirty(node);
    }

    /**
     * Splits an overflowing node and returns the split result.
     */
    private Split splitNode(Node node) throws IOException {

        int mid = node.keyCount / 2;

        long rightId = disk.allocatePage();
        Node right = new Node(node.isLeaf, rightId);
        right.initArrays(order);

        if (node.isLeaf) {

            int rightCount = node.keyCount - mid;

            System.arraycopy(node.keys, mid, right.keys, 0, rightCount);
            System.arraycopy(node.values, mid, right.values, 0, rightCount);

            right.keyCount = rightCount;
            node.keyCount = mid;

            // Maintain leaf-level linked list
            right.next = node.next;
            node.next = right.pageId;

        } else {
            int rightCount = node.keyCount - mid - 1;

            System.arraycopy(node.keys, mid + 1, right.keys, 0, rightCount);
            System.arraycopy(node.children, mid + 1, right.children, 0, rightCount + 1);

            right.keyCount = rightCount;
            node.keyCount = mid;
        }

        markDirty(node);
        markDirty(right);

        long promotedKey = node.isLeaf ? right.keys[0] : node.keys[mid];
        return new Split(promotedKey, right);
    }

    /**
     * Loads a node by page ID, using the cache when available.
     */
    Node load(long pageId) throws IOException {

        Node n = cache.get(pageId);
        if (n != null) {
            return n;
        }

        n = disk.readNode(pageId, order);
        cache.put(pageId, n);

        return n;
    }

    /**
     * Marks a node as dirty and updates it in the cache.
     */
    private void markDirty(Node node) {
        node.dirty = true;
        cache.put(node.pageId, node);
    }

    /**
     * Flushes all cached nodes and closes the underlying file.
     */
    public void close() throws IOException {
        cache.flushAll();
        disk.close();
    }

    /**
     * Prints a full structural representation of the tree.
     */
    public void printTree() throws IOException {
        System.out.println("B+Tree(root=" + rootPageId + "):");
        printNode(rootPageId, 0);
    }

    private void printNode(long pageId, int depth) throws IOException {

        Node n = load(pageId);
        String indent = "    ".repeat(depth);

        if (n.isLeaf) {
            System.out.print(indent + "Leaf(" + pageId + ") keys=[");
            for (int i = 0; i < n.keyCount; i++) {
                System.out.print(n.keys[i]);
                if (i < n.keyCount - 1) System.out.print(", ");
            }
            System.out.println("] next=" + n.next);

        } else {
            System.out.print(indent + "Internal(" + pageId + ") keys=[");
            for (int i = 0; i < n.keyCount; i++) {
                System.out.print(n.keys[i]);
                if (i < n.keyCount - 1) System.out.print(", ");
            }
            System.out.println("]");

            for (int i = 0; i <= n.keyCount; i++) {
                printNode(n.children[i], depth + 1);
            }
        }
    }

    /**
     * Prints the leaf-level linked list in key order.
     */
    public void printLeaves() throws IOException {

        Node node = load(rootPageId);

        while (!node.isLeaf) {
            node = load(node.children[0]);
        }

        System.out.println("Leaf chain:");

        while (true) {
            System.out.print("Leaf(" + node.pageId + "): ");
            for (int i = 0; i < node.keyCount; i++) {
                System.out.print(node.keys[i]);
                if (i < node.keyCount - 1) System.out.print(", ");
            }
            System.out.println();

            if (node.next == -1) break;
            node = load(node.next);
        }
    }
}
