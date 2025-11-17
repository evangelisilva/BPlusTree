package bptree;

import java.io.*;

/**
 * Handles all on-disk storage for the B+Tree.
 * <p>
 * Each node is stored in a fixed-size page. Page 0 is reserved for metadata and
 * stores the root page ID. All other pages store {@link Node} structures.
 * <p>
 * Pages are addressed by page ID, where:
 * <pre>
 *     fileOffset = pageId * pageSize
 * </pre>
 */
public class DiskManager {

    /** Underlying file used for storage. */
    private final RandomAccessFile file;

    /** Size in bytes of each fixed page. */
    private final int pageSize;

    /**
     * Opens (or creates) the on-disk storage file.
     *
     * @param path     file path for the B+Tree data file
     * @param pageSize size in bytes of each page
     * @throws IOException if the file cannot be created or opened
     */
    public DiskManager(String path, int pageSize) throws IOException {
        this.pageSize = pageSize;

        File f = new File(path);
        boolean fresh = (!f.exists() || f.length() == 0);

        this.file = new RandomAccessFile(f, "rw");

        // If file is new or empty, allocate the metadata page (page 0)
        if (fresh) {
            file.setLength(pageSize);
        }
    }

    /**
     * Returns whether the file contains only the metadata page.
     *
     * @return true if the file is newly created and no nodes exist yet
     * @throws IOException if file size cannot be read
     */
    public boolean isFresh() throws IOException {
        return file.length() == pageSize;
    }

    /**
     * Allocates a new empty page at the end of the file.
     *
     * @return the new page ID
     * @throws IOException if the file cannot be resized
     */
    public long allocatePage() throws IOException {
        long pageId = file.length() / pageSize;
        file.setLength(file.length() + pageSize);
        return pageId;
    }

    /**
     * Writes the root page ID into page 0.
     *
     * @param rootPageId page ID of the current root node
     * @throws IOException if writing fails
     */
    public void writeRootPage(long rootPageId) throws IOException {
        file.seek(0);
        file.writeLong(rootPageId);
    }

    /**
     * Reads the root page ID from page 0.
     *
     * @return the stored root page ID
     * @throws IOException if reading fails
     */
    public long readRootPage() throws IOException {
        file.seek(0);
        return file.readLong();
    }

    /**
     * Serializes a {@link Node} and writes it into the page identified by its pageId.
     * <p>
     * The node is serialized compactly using only the active keys and fields.
     *
     * @param n the node to write
     * @throws IOException if serialization exceeds page size or cannot be written
     */
    public void writeNode(Node n) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);

        out.writeBoolean(n.isLeaf);
        out.writeLong(n.next);
        out.writeInt(n.keyCount);

        for (int i = 0; i < n.keyCount; i++) {
            out.writeLong(n.keys[i]);
        }

        if (n.isLeaf) {
            for (int i = 0; i < n.keyCount; i++) {
                out.writeLong(n.values[i]);
            }
        } else {
            for (int i = 0; i <= n.keyCount; i++) {
                out.writeLong(n.children[i]);
            }
        }

        byte[] data = byteOut.toByteArray();
        if (data.length > pageSize) {
            throw new IOException("Page overflow: node size exceeds page capacity");
        }

        byte[] pageBuffer = new byte[pageSize];
        System.arraycopy(data, 0, pageBuffer, 0, data.length);

        file.seek(n.pageId * pageSize);
        file.write(pageBuffer);

        n.dirty = false;
    }

    /**
     * Reads a node from disk and reconstructs its in-memory structure.
     *
     * @param pageId page ID to read
     * @param order  maximum number of keys allowed in this B+Tree
     * @return deserialized node object
     * @throws IOException if reading fails
     */
    public Node readNode(long pageId, int order) throws IOException {
        byte[] page = new byte[pageSize];
        file.seek(pageId * pageSize);
        file.readFully(page);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(page));

        boolean isLeaf = in.readBoolean();
        long next = in.readLong();
        int keyCount = in.readInt();

        Node n = new Node(isLeaf, pageId);
        n.initArrays(order);
        n.keyCount = keyCount;
        n.next = next;

        for (int i = 0; i < keyCount; i++) {
            n.keys[i] = in.readLong();
        }

        if (isLeaf) {
            for (int i = 0; i < keyCount; i++) {
                n.values[i] = in.readLong();
            }
        } else {
            for (int i = 0; i <= keyCount; i++) {
                n.children[i] = in.readLong();
            }
        }

        return n;
    }

    /**
     * Closes the underlying data file.
     *
     * @throws IOException if the file cannot be closed
     */
    public void close() throws IOException {
        file.close();
    }
}
