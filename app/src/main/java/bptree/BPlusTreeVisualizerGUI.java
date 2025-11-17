package bptree;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Swing-based graphical viewer for a {@link BPlusTree}.
 * <p>
 * This GUI allows users to insert keys, search keys, and visually explore
 * the internal structure of the B+Tree, including its hierarchy and node
 * organization. The visualizer supports:
 * <ul>
 *   <li>Zooming using the mouse wheel</li>
 *   <li>Panning by dragging the mouse</li>
 *   <li>Automatic non-overlapping layout using a tidy tree algorithm</li>
 *   <li>Highlighting nodes that contain a searched key</li>
 * </ul>
 */
public class BPlusTreeVisualizerGUI extends JFrame {

    private final BPlusTree tree;
    private final TreePanel treePanel;

    /**
     * Creates a new GUI window to visualize the given B+Tree.
     *
     * @param tree the B+Tree instance to visualize
     */
    public BPlusTreeVisualizerGUI(BPlusTree tree) {
        super("B+Tree Visualizer");

        this.tree = tree;
        this.treePanel = new TreePanel(tree);

        setLayout(new BorderLayout());
        add(buildControlPanel(), BorderLayout.NORTH);
        add(new JScrollPane(treePanel), BorderLayout.CENTER);

        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    /**
     * Builds the top toolbar containing:
     * <ul>
     *     <li>A key input field</li>
     *     <li>Insert button</li>
     *     <li>Search button</li>
     *     <li>Redraw button</li>
     * </ul>
     *
     * @return a panel containing the controls
     */
    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JTextField input = new JTextField(10);

        JButton insertBtn = new JButton("Insert");
        JButton searchBtn = new JButton("Search");
        JButton redrawBtn = new JButton("Redraw");

        insertBtn.addActionListener(e -> {
            try {
                long key = Long.parseLong(input.getText());
                tree.insert(key, key * 100);
                treePanel.reloadTree();
                treePanel.repaint();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid input");
            }
        });

        searchBtn.addActionListener(e -> {
            try {
                long key = Long.parseLong(input.getText());
                Long val = tree.search(key);

                JOptionPane.showMessageDialog(this,
                        val == null
                                ? ("Key " + key + " NOT FOUND")
                                : ("Key " + key + " FOUND → value=" + val));

                treePanel.highlightKey(key);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid input");
            }
        });

        redrawBtn.addActionListener(e -> {
            treePanel.reloadTree();
            treePanel.repaint();
        });

        panel.add(new JLabel("Key:"));
        panel.add(input);
        panel.add(insertBtn);
        panel.add(searchBtn);
        panel.add(redrawBtn);

        return panel;
    }

    /**
     * Panel responsible for visualizing the B+Tree structure.
     * <p>
     * The layout algorithm works in three phases:
     * <ol>
     *     <li>Build a parallel in-memory DrawNode tree representing the Node structure</li>
     *     <li>Compute each node's text-box size based on its keys</li>
     *     <li>Compute subtree widths and assign positions using a tidy tree layout</li>
     * </ol>
     * <p>
     * The panel supports interactive zooming and dragging.
     */
    public static class TreePanel extends JPanel {

        /** Current zoom scale. */
        private double zoom = 1.0;

        /** Pan offset for dragging. */
        private int panX = 0, panY = 0;

        private int lastDragX, lastDragY;

        /** Key to be highlighted when searched. */
        private Long highlightKey = null;

        /** Wrapper class storing visual properties for each tree node. */
        private static class DrawNode {
            Node node;              // the underlying B+Tree node
            int x, y;               // final computed position
            int width;              // width of this node's subtree
            int boxW, boxH;         // pixel dimensions of rendered node box
            List<DrawNode> children = new ArrayList<>();
        }

        private DrawNode rootDrawNode = null;
        private final BPlusTree tree;

        // Layout tuning constants
        private static final int NODE_PADDING_X = 12;
        private static final int NODE_PADDING_Y = 6;
        private static final int LEVEL_HEIGHT = 90;
        private static final int SIBLING_SPACING = 25;

        /**
         * Creates the drawing panel responsible for layout and rendering.
         *
         * @param tree the B+Tree to visualize
         */
        public TreePanel(BPlusTree tree) {
            this.tree = tree;
            setBackground(Color.WHITE);

            // Zooming using mouse scroll
            addMouseWheelListener(e -> {
                zoom *= (e.getPreciseWheelRotation() < 0) ? 1.1 : 0.9;
                repaint();
            });

            // Panning using mouse drag
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    panX += e.getX() - lastDragX;
                    panY += e.getY() - lastDragY;
                    lastDragX = e.getX();
                    lastDragY = e.getY();
                    repaint();
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastDragX = e.getX();
                    lastDragY = e.getY();
                }
            });

            reloadTree();
        }

        /**
         * Rebuilds the drawable representation of the B+Tree.
         * This method:
         * <ol>
         *     <li>Loads the root</li>
         *     <li>Builds the DrawNode tree recursively</li>
         *     <li>Computes visual sizes</li>
         *     <li>Computes subtree widths</li>
         *     <li>Computes final layout positions</li>
         * </ol>
         */
        public void reloadTree() {
            try {
                rootDrawNode = build(tree.rootPageId);
                computeNodeSizes(rootDrawNode);
                computeSubtreeWidths(rootDrawNode);
                layoutTree(rootDrawNode, 0, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Highlights a key for the next repaint. Used when search() finds a match.
         *
         * @param key the key to highlight
         */
        public void highlightKey(long key) {
            this.highlightKey = key;
            repaint();
        }

        /**
         * Builds a DrawNode tree that mirrors the actual Node tree structure.
         *
         * @param pageId the node's page id on disk
         * @return a newly constructed DrawNode
         */
        private DrawNode build(long pageId) throws IOException {
            Node n = tree.load(pageId);
            DrawNode dn = new DrawNode();
            dn.node = n;

            // Build children recursively for internal nodes
            if (!n.isLeaf) {
                for (int i = 0; i <= n.keyCount; i++) {
                    dn.children.add(build(n.children[i]));
                }
            }

            return dn;
        }

        /**
         * Computes each node's pixel box size based on its rendered key text.
         */
        private void computeNodeSizes(DrawNode dn) {
            FontMetrics fm = getFontMetrics(getFont());

            // Join keys for measurement
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dn.node.keyCount; i++) {
                sb.append(dn.node.keys[i]);
                if (i < dn.node.keyCount - 1) sb.append(" | ");
            }

            int textWidth = fm.stringWidth(sb.toString());
            dn.boxW = textWidth + NODE_PADDING_X * 2;
            dn.boxH = fm.getHeight() + NODE_PADDING_Y * 2;

            // Recursively compute children
            for (DrawNode c : dn.children) {
                computeNodeSizes(c);
            }
        }

        /**
         * Computes subtree widths bottom-up.
         * The width of a subtree is the sum of child subtree widths plus spacing.
         *
         * @return the computed width for this subtree
         */
        private int computeSubtreeWidths(DrawNode dn) {
            if (dn.children.isEmpty()) {
                dn.width = dn.boxW + SIBLING_SPACING;
                return dn.width;
            }

            int total = 0;
            for (DrawNode c : dn.children)
                total += computeSubtreeWidths(c);

            total += SIBLING_SPACING * (dn.children.size() - 1);

            dn.width = Math.max(total, dn.boxW);
            return dn.width;
        }

        /**
         * Assigns x/y positions for each DrawNode using a tidy, non-overlapping layout.
         *
         * @param dn    the node being positioned
         * @param left  left boundary of this subtree
         * @param depth tree depth (controls vertical spacing)
         */
        private void layoutTree(DrawNode dn, int left, int depth) {
            dn.y = depth * LEVEL_HEIGHT + 50;
            dn.x = left + dn.width / 2;

            int cursor = left;
            for (DrawNode c : dn.children) {
                layoutTree(c, cursor, depth + 1);
                cursor += c.width + SIBLING_SPACING;
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;

            // Apply pan + zoom transforms
            g.translate(panX, panY);
            g.scale(zoom, zoom);

            if (rootDrawNode != null) {
                drawTree(g, rootDrawNode);
            }
        }

        /**
         * Draws the subtree rooted at dn.
         */
        private void drawTree(Graphics2D g, DrawNode dn) {
            int bx = dn.x - dn.boxW / 2;
            int by = dn.y;

            // Draw edges to children
            g.setColor(Color.DARK_GRAY);
            for (DrawNode c : dn.children) {
                g.drawLine(dn.x, dn.y + dn.boxH, c.x, c.y);
                drawTree(g, c);
            }

            // Background color: leaf nodes = blueish, internal = yellowish
            if (dn.node.isLeaf)
                g.setColor(new Color(180, 220, 245));
            else
                g.setColor(new Color(255, 250, 200));

            g.fillRoundRect(bx, by, dn.boxW, dn.boxH, 12, 12);

            // Highlight if key matches search
            if (highlightKey != null) {
                for (int i = 0; i < dn.node.keyCount; i++) {
                    if (dn.node.keys[i] == highlightKey) {
                        g.setColor(new Color(255, 255, 0, 150));
                        g.fillRoundRect(bx, by, dn.boxW, dn.boxH, 12, 12);
                        break;
                    }
                }
            }

            // Node border
            g.setColor(Color.BLACK);
            g.drawRoundRect(bx, by, dn.boxW, dn.boxH, 12, 12);

            // Draw text (keys)
            FontMetrics fm = g.getFontMetrics();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dn.node.keyCount; i++) {
                sb.append(dn.node.keys[i]);
                if (i < dn.node.keyCount - 1) sb.append(" ");
            }

            int tx = dn.x - fm.stringWidth(sb.toString()) / 2;
            int ty = dn.y + fm.getAscent() + NODE_PADDING_Y;

            g.setColor(Color.BLACK);
            g.drawString(sb.toString(), tx, ty);
        }
    }

    /**
     * Launches the visualizer with a small test tree.
     * This is mainly for demonstration and manual GUI testing.
     */
    public static void main(String[] args) {
        try {
            BPlusTree tree = new BPlusTree("vis.db", 256);

            for (int i = 1; i <= 200; i++) {
                tree.insert(i, i * 10);
            }

            new BPlusTreeVisualizerGUI(tree);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
