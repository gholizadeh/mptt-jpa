package works.hacker.model;

import works.hacker.mptt.MpttEntity;

import javax.persistence.*;
import java.util.Objects;

/** Demo usage of the {@link works.hacker.mptt.MpttEntity}.
 * <p>
 * Also used in the unit / integrations tests, as the {@link works.hacker.mptt.MpttEntity} is annotated
 * with {@link javax.persistence.MappedSuperclass} and can not be used standalone.
 *
 * @see works.hacker.repo.TagTreeRepositoryCustom
 * @see works.hacker.repo.TagTreeRepositoryImpl
 * @see works.hacker.repo.TagTreeRepository
 * @see <a href="https://github.com/hacker-works/mptt-jpa">README</a>
 */
@Entity
public class TagTree extends MpttEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @Column(nullable = false)
  private final String name;

  public TagTree(String name) {
    super();
    this.name = name;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return String.format("%s (id: %d) %s", getName(),  getId(), super.toString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.toString());
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object o) {
    return this.toString().equals(o.toString());
  }
}