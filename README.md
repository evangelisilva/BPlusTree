# **Disk-Backed B+Tree**

A fully functional **disk-based B+Tree index** implemented in Java, featuring:

- Durable on-disk page storage
- LRU page cache with **hits, misses, and evictions**
- Leaf-level linked list for efficient range scans
- Pretty-print tree and leaf-chain inspection
- Swing-based **B+Tree visualizer**
- Clean Gradle project using Java 21

This project aims to closely model how real database systems implement B+Trees and buffer pools.

## Features

### Disk-backed storage  
Nodes live in fixed-size pages (default: 4KB).  
`DiskManager` persists nodes and maintains a metadata page for the root pointer.

### Insert & search support  
- Full recursive insert algorithm  
- Splitting logic for leaf & internal nodes  
- Promotion of median keys  
- Binary search inside nodes for fast access

### Leaf chain  
Leaves store a `next` pointer supporting fast sequential scans.

### Pretty Printing  
- `printTree()` shows full hierarchical structure  
- `printLeaves()` displays keys in sorted leaf-chain order

### Swing Visualizer  
`BPlusTreeVisualizerGUI` visually renders the tree with a tidy layout algorithm.

