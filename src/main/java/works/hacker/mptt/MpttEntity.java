package works.hacker.mptt;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

/**
 * Provides the properties needed to maintain the MPTT (Modified Preorder Tree Traversal) data structure.
 * <p>
 * In essence MPTT allows fast read operations on hierarchical tree-like data structures stored in an SQL
 * database.
 * <p>
 * Modelling such data structure of nested sets in SQL is done by using the following properties:
 * <ul>
 * <li><b>lft</b> and <b>rgt</b> to represent the nesting of the nodes</li>
 * <li><b>treeId</b> to allow growing multiple trees / hierarchies and discriminate between trees</li>
 * </ul>
 * <p>
 * <b>NOTE:</b> left and right are reserved words in MySQL, thus the use of <b>lft</b> and <b>rgt</b>.
 * <p>
 * These properties are managed by the implementation of the {@link MpttRepository}.
 * <p>
 * Usage:
 * <pre><code>
 * {@literal @}Entity
 *  public class TagTree extends MpttEntity {
 *    //...
 *    public TagTree(String name) {
 *      super(); // important to call super to set the MpttEntity-instance defaults
 *      this.name = name;
 *    }
 *
 *    // The unit tests make good use of these overrides:
 *   {@literal @}Override
 *    public String toString() {
 *      return String.format("%s (id: %d) %s", getName(),  getId(), super.toString());
 *    }
 *
 *   {@literal @}Override
 *    public int hashCode() {
 *      return Objects.hash(this.toString());
 *    }
 *
 *   {@literal @}SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
 *   {@literal @}Override
 *    public boolean equals(Object o) {
 *      return this.toString().equals(o.toString());
 *    }
 *  }
 * </code></pre>
 *
 * @see works.hacker.model.TagTree demo sample extending MpttEntity
 * @see MpttRepository
 * @see MpttRepositoryImpl
 * @see <a href="https://github.com/hacker-works/mptt-jpa">README</a>
 */
@MappedSuperclass
public class MpttEntity {
  public static final long NO_TREE_ID = -1L;
  public static final long DEFAULT_LFT = 1L;
  public static final long DEFAULT_RGT = 2L;
  public static final long DEFAULT_DEPTH = 0L;

  @Column(nullable = false)
  private long treeId;

  @Column(nullable = false)
  private long lft;

  @Column(nullable = false)
  private long rgt;

  @Column(nullable = false)
  private long depth;

  public MpttEntity() {
    this.treeId = NO_TREE_ID;
    this.lft = DEFAULT_LFT;
    this.rgt = DEFAULT_RGT;
    this.depth = DEFAULT_DEPTH;
  }

  public boolean hasTreeId() {
    return treeId != NO_TREE_ID;
  }

  public Long getTreeId() {
    return treeId;
  }

  public void setTreeId(Long treeId) {
    this.treeId = treeId;
  }

  public long getLft() {
    return lft;
  }

  public void setLft(long lft) {
    this.lft = lft;
  }

  public long getRgt() {
    return rgt;
  }

  public void setRgt(long rgt) {
    this.rgt = rgt;
  }

  public long getDepth() {
    return depth;
  }

  public void setDepth(long depth) {
    this.depth = depth;
  }

  @Override
  public String toString() {
    return String.format("[treeId: %d | lft: %d | rgt: %d]", getTreeId(), getLft(), getRgt());
  }
}
