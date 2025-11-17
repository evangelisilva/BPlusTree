package bptree;

/**
 * Represents the result of splitting a full B+Tree node.
 * <p>
 * When a node is split, the median key is pushed up into the parent,
 * and the right-hand node produced by the split is returned as {@link #newRight}.
 */
public class Split {

    /**
     * The key that must be inserted into the parent node.
     * For leaf splits, this is the first key of the new right node.
     * For internal splits, this is the median separator key.
     */
    public final long key;

    /**
     * The newly created right-side sibling node resulting from the split.
     */
    public final Node newRight;

    /**
     * Creates a new split result.
     *
     * @param key      key to be propagated upward to the parent
     * @param newRight the right-hand node created during the split
     */
    public Split(long key, Node newRight) {
        this.key = key;
        this.newRight = newRight;
    }
}
