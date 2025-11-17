package bptree;

/**
 * Demonstrates basic usage of the B+Tree implementation.
 * <p>
 * This example:
 * <ul>
 *     <li>Creates a B+Tree backed by a disk file</li>
 *     <li>Inserts several key/value pairs</li>
 *     <li>Performs a few search operations</li>
 *     <li>Prints the tree structure and leaf chain</li>
 * </ul>
 */
public class BPlusTreeDemo {

    public static void main(String[] args) {
        try {
            /*
             * Create a B+Tree using "tree.db" as the underlying file
             * and a 512-byte LRU cache (2 pages if page size is 256).
             */
            BPlusTree tree = new BPlusTree("tree.db", 512);

            // Insert sample data
            for (long i = 1; i <= 200; i++) {
                tree.insert(i, i * 100);
            }

            // Perform example searches
            System.out.println("Search 1 = " + tree.search(1L));
            System.out.println("Search 200 = " + tree.search(200L));   // expected: null
            System.out.println("Search 1500 = " + tree.search(1500L)); // expected: null
            System.out.println("Search 3000 = " + tree.search(3000L)); // expected: null

            // Print the entire tree
            tree.printTree();

            // Print ordered leaf chain
            tree.printLeaves();

            // Flush cache + close underlying file
            tree.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
