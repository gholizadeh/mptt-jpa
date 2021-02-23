package works.hacker.repo.dyadic;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import works.hacker.config.TreesJpaConfig;
import works.hacker.model.dyadic.DyadicNode;
import works.hacker.mptt.TreeEntity;
import works.hacker.mptt.TreeRepository;
import works.hacker.mptt.TreeUtils;
import works.hacker.mptt.classic.MpttRepository;
import works.hacker.mptt.dyadic.DyadicEntity;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SuppressWarnings("ALL")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {TreesJpaConfig.class}, loader = AnnotationConfigContextLoader.class)
@Transactional
@DirtiesContext
public class DyadicNodeRepoTest {
  private final Logger LOG = LoggerFactory.getLogger(DyadicNodeRepoTest.class);

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Resource
  DyadicNodeRepository treeRepo;

  protected TreeUtils<DyadicNode> utils;

  @Before
  public void init() {
    treeRepo.setEntityClass(DyadicNode.class);
    utils = new TreeUtils<>(treeRepo);
  }

  @Test
  public void giveSaved_whenFindByName_thenOK() {
    assertThat(treeRepo.count(), is(0L));

    DyadicNode expected = new DyadicNode("test-01");
    treeRepo.save(expected);
    assertThat(treeRepo.count(), is(1L));

    DyadicNode actual = treeRepo.findByName(expected.getName());
    assertThat(actual.getId(), is(notNullValue()));
    assertThat(actual.getName(), is(expected.getName()));
  }

  @Test
  public void givenNoTree_whenConstructed_thenHasNoTreeId() {
    DyadicNode actual = new DyadicNode("test");
    assertThat(actual.hasTreeId(), is(false));
  }

  @Test
  public void givenNoTree_whenStartTree_thenOK() {
    TreeWithNoChildren<DyadicNode> tree = new TreeWithNoChildren<>(treeRepo, utils);

    assertThat(treeRepo.count(), is(1L));

    DyadicNode actual = treeRepo.findByName(tree.root.getName());
    assertThat(actual.getTreeId(), not(TreeEntity.NO_TREE_ID));
    assertThat(actual.getTreeId(), is(tree.treeId));

    assertThat(actual.getLft(), is(actual.getStartLft()));
    assertThat(actual.getRgt(), is(actual.getStartRgt()));

    assertThat(actual.getDepth(), is(DyadicEntity.START));

    assertThat(actual.getLftN(), is(DyadicEntity.START));
    assertThat(actual.getLftD(), is(DyadicEntity.END));

    assertThat(actual.getRgtN(), is(DyadicEntity.END));
    assertThat(actual.getRgtD(), is(DyadicEntity.END));

    assertThat(actual.getLft(), is(0.0));
    assertThat(actual.getRgt(), is(1.0));
  }

  @Test
  public void givenTree_whenStartTreeWithUsedRootNode_thenError()
      throws TreeRepository.NodeAlreadyAttachedToTree {
    TreeWithNoChildren<DyadicNode> tree = new TreeWithNoChildren<>(treeRepo, utils);

    exceptionRule.expect(MpttRepository.NodeAlreadyAttachedToTree.class);
    exceptionRule.expectMessage(String.format("Node already has treeId set to %d", tree.treeId));
    DyadicNode root = treeRepo.findByName(tree.root.getName());
    treeRepo.startTree(root);
  }

  @Test
  public void givenTree_whenFindTreeRoot_thenOK() {
    TreeWithNoChildren<DyadicNode> tree = new TreeWithNoChildren<>(treeRepo, utils);
    DyadicNode actual = treeRepo.findTreeRoot(tree.treeId);
    assertThat(actual, is(tree.root));
  }

  @Test
  public void givenParentNodeNotAttachedToTree_whenAddChild_thenError()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeAlreadyAttachedToTree {
    DyadicNode parent = new DyadicNode("parent");
    DyadicNode child = new DyadicNode("child");

    exceptionRule.expect(TreeRepository.NodeNotInTree.class);
    exceptionRule.expectMessage(String.format("Parent node not attached to any tree: %s", parent));
    treeRepo.addChild(parent, child);
  }

  @Test
  public void givenChildIsTreeRoot_whenAddChild_thenError()
      throws TreeRepository.NodeAlreadyAttachedToTree, TreeRepository.NodeNotInTree {
    DyadicNode parent = new DyadicNode("parent");
    DyadicNode child = new DyadicNode("child");

    treeRepo.startTree(parent);
    Long treeId = treeRepo.startTree(child);

    exceptionRule.expect(MpttRepository.NodeAlreadyAttachedToTree.class);
    exceptionRule.expectMessage(String.format("Node already has treeId set to %d", treeId));
    treeRepo.addChild(parent, child);
  }


  @Test
  public void givenEmptyTree_whenFindYoungestChild_thenOptionalEmpty()
      throws TreeRepository.NodeAlreadyAttachedToTree {
    DyadicNode root = new DyadicNode("root");
    treeRepo.startTree(root);

    Optional<DyadicNode> actual = treeRepo.findYoungestChild(root);
    assertThat(actual, is(Optional.empty()));
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Test
  public void givenEmptyTree_whenAddChild_thenOK()
      throws TreeRepository.NodeAlreadyAttachedToTree, TreeRepository.NodeNotInTree {
    DyadicNode root = new DyadicNode("root");
    treeRepo.startTree(root);

    DyadicNode child = new DyadicNode("child");
    treeRepo.addChild(root, child);

    assertThat(treeRepo.count(), is(2L));

    DyadicNode actualRoot = treeRepo.findByName("root");
    DyadicNode actualChild = treeRepo.findByName("child");

    assertThat(actualRoot.getLft(), is(0.0));
    assertThat(actualRoot.getRgt(), is(1.0));
    assertThat(actualChild.getTreeId(), is(root.getTreeId()));
    assertThat(actualChild.getLft(), is(root.getLft()));
    assertThat(actualChild.getRgt(), is((root.getLft() + root.getRgt()) / 2));

    Optional<DyadicNode> youngestChild = treeRepo.findYoungestChild(actualRoot);
    assertThat(youngestChild.get(), is(child));
  }

  @Test
  public void givenTreeWithoutChildren_whenPrintTree_thenOK() {
    TreeWithNoChildren<DyadicNode> tree = new TreeWithNoChildren<>(treeRepo, utils);
    assertThat(utils.printTree(tree.root), is(tree.getExpected()));
  }

  @Test
  public void givenTreeWithOneChild_whenFindChildren_thenContainsOneChild() {
    TreeWithOneChild<DyadicNode> tree = new TreeWithOneChild<>(treeRepo, utils);
    List<DyadicNode> actual = treeRepo.findChildren(tree.root);
    assertThat(actual, containsInRelativeOrder(tree.child1));
  }

  @Test
  public void givenTreeWithChild_whenPrintTree_thenOK() {
    TreeWithOneChild<DyadicNode> tree = new TreeWithOneChild<>(treeRepo, utils);
    String actual = utils.printTree(tree.root);
    assertThat(actual, is(tree.getExpected()));
  }

  @Test
  public void givenTreeWithTwoChildren_whenFindChildren_thenContainsTwoChildren() {
    TreeWithTwoChildren<DyadicNode> tree = new TreeWithTwoChildren<>(treeRepo, utils);
    List<DyadicNode> actual = treeRepo.findChildren(tree.root);
    assertThat(actual, containsInRelativeOrder(tree.child1, tree.child2));
  }

  @Test
  public void givenTreeWithTwoChildren_whenPrintTree_thenOK() {
    TreeWithTwoChildren<DyadicNode> tree = new TreeWithTwoChildren<>(treeRepo, utils);
    String actual = utils.printTree(tree.root);
    assertThat(actual, is(tree.getExpected()));
  }

  @Test
  public void givenTreeWithChildAndSubChild_whenFindChildren_thenContainsOneChild() {
    TreeWithChildAndSubChild<DyadicNode> tree = new TreeWithChildAndSubChild<>(treeRepo, utils);
    List<DyadicNode> actual = treeRepo.findChildren(tree.root);
    assertThat(actual.size(), is(1));
    assertThat(actual, containsInRelativeOrder(tree.child1));
  }

  @Test
  public void givenTreeWithChildAndSubChild_whenPrintTree_thenOK() {
    TreeWithChildAndSubChild<DyadicNode> tree = new TreeWithChildAndSubChild<>(treeRepo, utils);
    String actual = utils.printTree(tree.root);
    assertThat(actual, is(tree.getExpected()));
  }

  @Test
  public void givenComplexTree1_whenPrintTree_thenOK() {
    ComplexTree1<DyadicNode> tree = new ComplexTree1<>(treeRepo, utils);
    String actual = utils.printTree(tree.root);
    assertThat(actual, is(tree.getExpected()));
  }

  @Test
  public void givenComplexTree1_whenFindChildren_thenContainsTwoChildren() {
    ComplexTree1<DyadicNode> tree = new ComplexTree1<>(treeRepo, utils);
    List<DyadicNode> actual = treeRepo.findChildren(tree.root);
    assertThat(actual.size(), is(2));
    assertThat(actual, containsInRelativeOrder(tree.child1, tree.child2));
  }

  @Test
  public void givenComplexTree2_whenPrintTree_thenOK() {
    ComplexTree2<DyadicNode> tree = new ComplexTree2<>(treeRepo, utils);
    String actual = utils.printTree(tree.root);
    assertThat(actual, is(tree.getExpected()));
  }

  @Test
  public void givenComplexTree2_whenFindChildren_thenOK() {
    ComplexTree2<DyadicNode> tree = new ComplexTree2<>(treeRepo, utils);

    List<DyadicNode> actual1 = treeRepo.findChildren(tree.root);
    assertThat(actual1.size(), is(2));
    assertThat(actual1, containsInRelativeOrder(tree.child1, tree.child2));

    List<DyadicNode> actual2 = treeRepo.findChildren(tree.child1);
    assertThat(actual2.size(), is(1));
    assertThat(actual2, contains(tree.subChild1));

    List<DyadicNode> actual3 = treeRepo.findChildren(tree.subChild1);
    assertThat(actual3.size(), is(1));
    assertThat(actual3, contains(tree.subSubChild1));
  }

  @Test
  public void givenComplexTree3_whenPrintTree_thenOK() {
    ComplexTree3<DyadicNode> tree = new ComplexTree3<>(treeRepo, utils);

    String actual = utils.printTree(tree.root);
    assertThat(actual, is(tree.getExpected()));

    String actualPartial = utils.printTree(tree.child1);
    assertThat(actualPartial, is(tree.getExpectedPartial()));
  }

  @Test
  public void givenComplexTree3_whenFindChildren_thenOK() {
    ComplexTree3<DyadicNode> tree = new ComplexTree3<>(treeRepo, utils);

    List<DyadicNode> actual1 = treeRepo.findChildren(tree.root);
    assertThat(actual1.size(), is(2));
    assertThat(actual1, containsInRelativeOrder(tree.child1, tree.child2));

    List<DyadicNode> actual2 = treeRepo.findChildren(tree.child1);
    assertThat(actual2.size(), is(2));
    assertThat(actual2, containsInRelativeOrder(tree.subChild1, tree.subChild2));
  }

  @Test
  public void givenParentNotAttachedToTree_whenRemoveChild_thenError()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    DyadicNode parent = new DyadicNode("parent");
    DyadicNode child = new DyadicNode("child");

    exceptionRule.expect(MpttRepository.NodeNotInTree.class);
    exceptionRule.expectMessage(String.format("Parent node not attached to any tree: %s", parent));
    treeRepo.removeChild(parent, child);
  }

  @Test
  public void givenParentAndChildInDifferentTrees_whenRemoveChild_thenError()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    TreeWithOneChild<DyadicNode> tree1 = new TreeWithOneChild<>(treeRepo, utils);
    TreeWithOneChild<DyadicNode> tree2 = new TreeWithOneChild<>(treeRepo, utils);

    exceptionRule.expect(MpttRepository.NodeNotInTree.class);
    exceptionRule
        .expectMessage(
            String.format("Nodes not in same tree - parent: %s; child %s", tree1.root, tree2.child1));
    treeRepo.removeChild(tree1.root, tree2.child1);
  }

  @Test
  public void givenParentAndChild_whenRemoveChildReverseParentAndChild_thenError()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    TreeWithOneChild<DyadicNode> tree = new TreeWithOneChild<>(treeRepo, utils);

    exceptionRule.expect(MpttRepository.NodeNotChildOfParent.class);
    treeRepo.removeChild(tree.child1, tree.root);
  }

  @Test
  public void givenTreeWithOneChild_whenRemoveChild_thenOK()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    TreeWithOneChild<DyadicNode> tree = new TreeWithOneChild<>(treeRepo, utils);

    LOG.debug(String.format("before:\n%s", utils.printTree(tree.root)));
    List<DyadicNode> removed = treeRepo.removeChild(tree.root, tree.child1);
    LOG.debug(String.format("after\n%s", utils.printTree(tree.root)));

    DyadicNode actual = treeRepo.findByName(tree.root.getName());
    assertThat(actual.getLft(), is(actual.getStartLft()));
    assertThat(actual.getRgt(), is(actual.getStartRgt()));

    assertThat(treeRepo.findChildren(actual), is(emptyIterable()));

    assertThat(treeRepo.count(), is(1L));

    assertThat(removed.size(), is(1));
    assertThat(removed, contains(tree.child1));
  }

  @Test
  public void givenTreeWithChildAndSubChild_whenRemoveChild_thenOK()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    TreeWithChildAndSubChild<DyadicNode> tree = new TreeWithChildAndSubChild<>(treeRepo, utils);

    LOG.debug(String.format("before:\n%s", utils.printTree(tree.root)));
    List<DyadicNode> removed = treeRepo.removeChild(tree.root, tree.child1);
    LOG.debug(String.format("after:\n%s", utils.printTree(tree.root)));

    DyadicNode actual = treeRepo.findByName(tree.root.getName());
    assertThat(actual.getLft(), is(actual.getStartLft()));
    assertThat(actual.getRgt(), is(actual.getStartRgt()));

    assertThat(treeRepo.findChildren(actual), is(emptyIterable()));

    assertThat(treeRepo.count(), is(1L));

    assertThat(removed.size(), is(2));
    assertThat(removed, contains(tree.child1, tree.subChild1));
  }

  @Test
  public void givenTreeWithTwoChildren_whenRemoveChild_thenOK()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    TreeWithTwoChildren<DyadicNode> tree = new TreeWithTwoChildren<>(treeRepo, utils);

    LOG.debug(String.format("before:\n%s", utils.printTree(tree.root)));
    List<DyadicNode> removed = treeRepo.removeChild(tree.root, tree.child1);
    LOG.debug(String.format("after:\n%s", utils.printTree(tree.root)));

    List<DyadicNode> actualChildren = treeRepo.findChildren(tree.root);
    assertThat(actualChildren.size(), is(1));
    assertThat(actualChildren, contains(tree.child2));

    assertThat(treeRepo.count(), is(2L));

    assertThat(removed.size(), is(1));
    assertThat(removed, contains(tree.child1));
  }

  @Test
  public void givenTreeWithTwoChildren_whenRemoveChild_whenAddChild_thenOK()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent,
      TreeRepository.NodeAlreadyAttachedToTree {
    TreeWithTwoChildren<DyadicNode> tree = new TreeWithTwoChildren<>(treeRepo, utils);

    LOG.debug(String.format("before remove:\n%s", utils.printTree(tree.root)));
    treeRepo.removeChild(tree.root, tree.child1);
    LOG.debug(String.format("after remove:\n%s", utils.printTree(tree.root)));

    DyadicNode newChild = new DyadicNode("newChild");
    treeRepo.addChild(tree.root, newChild);

    // @formatter:off
    String expected = String.format(
        ".\n" +
        "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n" +
        "    ├── child-2 (id: %d) [treeId: %d | lft: 1/2 | rgt: 3/4]\n" +
        "    └── newChild (id: %d) [treeId: %d | lft: 3/4 | rgt: 7/8]",
        tree.root.getId(), tree.root.getTreeId(),
        tree.child2.getId(), tree.child2.getTreeId(),
        newChild.getId(),  newChild.getTreeId());
    // @formatter:on
    String actual = utils.printTree(tree.root);
    LOG.debug(String.format("after add:\n%s", actual));

    assertThat(actual, is(expected));
  }

  @Test
  public void givenTreeWithChildAndSubChild_whenRemoveSubChild_thenOK()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    TreeWithChildAndSubChild<DyadicNode> tree = new TreeWithChildAndSubChild<>(treeRepo, utils);

    LOG.debug(String.format("before:\n%s", utils.printTree(tree.root)));
    List<DyadicNode> removed = treeRepo.removeChild(tree.root, tree.subChild1);
    LOG.debug(String.format("after:\n%s", utils.printTree(tree.root)));

    List<DyadicNode> actualChildren = treeRepo.findChildren(tree.root);
    assertThat(actualChildren.size(), is(1));
    assertThat(actualChildren, contains(tree.child1));

    assertThat(treeRepo.count(), is(2L));

    assertThat(removed.size(), is(1));
    assertThat(removed, contains(tree.subChild1));

    assertThat(treeRepo.findChildren(tree.child1), is(empty()));
  }

  @Test
  public void givenComplexTree3_whenRemoveChild1_thenOK()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    ComplexTree3<DyadicNode> tree = new ComplexTree3<>(treeRepo, utils);

    LOG.debug(String.format("before:\n%s", utils.printTree(tree.root)));
    treeRepo.removeChild(tree.root, tree.child1);
    LOG.debug(String.format("after:\n%s", utils.printTree(tree.root)));

    String actual = utils.printTree(tree.root);
    assertThat(actual, is(tree.getExpectedAfterChild1Removal()));
  }

  @Test
  public void givenComplexTree3_whenRemoveChild2_thenOK()
      throws TreeRepository.NodeNotInTree, TreeRepository.NodeNotChildOfParent {
    ComplexTree3<DyadicNode> tree = new ComplexTree3<>(treeRepo, utils);

    LOG.debug(String.format("before:\n%s", utils.printTree(tree.root)));
    treeRepo.removeChild(tree.root, tree.child2);
    LOG.debug(String.format("after:\n%s", utils.printTree(tree.root)));

    String actual = utils.printTree(tree.root);
    assertThat(actual, is(tree.getExpectedAfterChild2Removal()));
  }

  @Test
  public void givenComplexTree3_whenFindTreeRoot_thenOK() {
    ComplexTree3<DyadicNode> tree = new ComplexTree3<>(treeRepo, utils);

    LOG.debug(String.format("tree to search for root:\n%s", utils.printTree(tree.root)));

    DyadicNode actual = treeRepo.findTreeRoot(tree.treeId);
    assertThat(actual, is(tree.root));
  }

  @Test
  public void givenRoot_whenFindAncestorsOfRoot_thenEmptyList() {
    TreeWithNoChildren<DyadicNode> tree = new TreeWithNoChildren<>(treeRepo, utils);
    List<DyadicNode> actual = treeRepo.findAncestors(tree.root);
    assertThat(actual, is(empty()));
  }

  @Test
  public void givenTreeWithOneChild_whenFindAncestorsOfChild_thenListOfRoot() {
    TreeWithOneChild<DyadicNode> tree = new TreeWithOneChild<>(treeRepo, utils);
    List<DyadicNode> actual = treeRepo.findAncestors(tree.child1);
    assertThat(actual.size(), is(1));
    assertThat(actual, contains(tree.root));
  }

  @Test
  public void givenTreeWithChildAndSubChild_whenFindAncestors_thenOK() {
    TreeWithChildAndSubChild<DyadicNode> tree = new TreeWithChildAndSubChild<>(treeRepo, utils);

    List<DyadicNode> ancestorsOfRoot = treeRepo.findAncestors(tree.root);
    assertThat(ancestorsOfRoot, is(empty()));

    List<DyadicNode> ancestorsOfChild = treeRepo.findAncestors(tree.child1);
    assertThat(ancestorsOfChild.size(), is(1));
    assertThat(ancestorsOfChild, contains(tree.root));

    List<DyadicNode> ancestorsOfSubChild = treeRepo.findAncestors(tree.subChild1);
    assertThat(ancestorsOfSubChild.size(), is(2));
    assertThat(ancestorsOfSubChild, containsInRelativeOrder(tree.root, tree.child1));
  }

  @Test
  public void givenComplexTree3_whenFindAncestors_thenOK() {
    ComplexTree3<DyadicNode> tree = new ComplexTree3<>(treeRepo, utils);
    assertThat(treeRepo.findAncestors(tree.subChild1), containsInRelativeOrder(tree.root, tree.child1));
    assertThat(treeRepo.findAncestors(tree.subChild2), containsInRelativeOrder(tree.root, tree.child1));
    assertThat(treeRepo.findAncestors(tree.subSubChild1),
        containsInRelativeOrder(tree.root, tree.child1, tree.subChild1));
  }

  @Test
  public void givenRoot_whenFindParentOfRoot_thenNull() throws TreeRepository.NodeAlreadyAttachedToTree {
    DyadicNode root = new DyadicNode("root");
    treeRepo.startTree(root);
    assertThat(treeRepo.findParent(root), is(Optional.empty()));
  }

  @Test
  public void givenTreeWithOneChild_whenFindParentOfChild_thenRoot() {
    TreeWithOneChild<DyadicNode> tree = new TreeWithOneChild<>(treeRepo, utils);
    assertThat(treeRepo.findParent(tree.root), is(Optional.empty()));
    assertThat(treeRepo.findParent(tree.child1).get(), is(tree.root));
  }

  @Test
  public void givenTreeWithChildAndSubChild_whenFindParent_thenOK() {
    TreeWithChildAndSubChild<DyadicNode> tree = new TreeWithChildAndSubChild<>(treeRepo, utils);
    assertThat(treeRepo.findParent(tree.root), is(Optional.empty()));
    assertThat(treeRepo.findParent(tree.child1).get(), is(tree.root));
    assertThat(treeRepo.findParent(tree.subChild1).get(), is(tree.child1));
  }

  @Test
  public void givenTreeWithTwoChildren_whenFindParent_thenOK() {
    TreeWithTwoChildren<DyadicNode> tree = new TreeWithTwoChildren<>(treeRepo, utils);
    assertThat(treeRepo.findParent(tree.root), is(Optional.empty()));
    assertThat(treeRepo.findParent(tree.child1).get(), is(tree.root));
    assertThat(treeRepo.findParent(tree.child2).get(), is(tree.root));
  }

  @Test
  public void givenComplexTree3_whenFindParent_thenOK() {
    ComplexTree3<DyadicNode> tree = new ComplexTree3<>(treeRepo, utils);
    assertThat(treeRepo.findParent(tree.root), is(Optional.empty()));
    assertThat(treeRepo.findParent(tree.child1).get(), is(tree.root));
    assertThat(treeRepo.findParent(tree.child2).get(), is(tree.root));
    assertThat(treeRepo.findParent(tree.subChild1).get(), is(tree.child1));
    assertThat(treeRepo.findParent(tree.subChild2).get(), is(tree.child1));
    assertThat(treeRepo.findParent(tree.subSubChild1).get(), is(tree.subChild1));
    assertThat(treeRepo.findParent(tree.lastSubChild).get(), is(tree.child2));
  }

  @SuppressWarnings("rawtypes")
  static class TreeWithNoChildren<T extends TreeEntity> {
    public T root;

    protected Long treeId;

    protected final TreeRepository<T> repo;
    protected final TreeUtils<T> utils;

    public TreeWithNoChildren(TreeRepository<T> repo, TreeUtils<T> utils) {
      this.repo = repo;
      this.utils = utils;

      try {
        setupTree();
      } catch (Exception e) {
        // do nothing
      }
    }

    protected void setupTree()
        throws TreeRepository.NodeAlreadyAttachedToTree, InvocationTargetException,
        NoSuchMethodException, InstantiationException, IllegalAccessException,
        TreeRepository.NodeNotInTree {
      root = repo.createNode("root");

      this.treeId = repo.startTree(root);
    }

    public String getExpected() {
      // @formatter:off
      return String.format(
          ".\n" +
          "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]",
          root.getId(), root.getTreeId());
      // @formatter:on
    }
  }

  @SuppressWarnings("rawtypes")
  static class TreeWithOneChild<T extends TreeEntity> extends TreeWithNoChildren<T> {
    public T child1;

    public TreeWithOneChild(TreeRepository<T> repo, TreeUtils<T> utils) {
      super(repo, utils);
    }

    protected void setupTree()
        throws TreeRepository.NodeAlreadyAttachedToTree, TreeRepository.NodeNotInTree,
        InvocationTargetException, NoSuchMethodException, InstantiationException,
        IllegalAccessException {
      super.setupTree();
      child1 = repo.createNode("child-1");
      repo.addChild(root, child1);
    }

    @Override
    public String getExpected() {
      // @formatter:off
      return String.format(
          ".\n" +
          "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n"+
          "    └── child-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/2]",
          root.getId(), root.getTreeId(),
          child1.getId(), child1.getTreeId());
      // @formatter:on
    }
  }

  @SuppressWarnings("rawtypes")
  static class TreeWithTwoChildren<T extends TreeEntity> extends TreeWithOneChild<T> {
    public T child2;

    public TreeWithTwoChildren(TreeRepository<T> repo, TreeUtils<T> utils) {
      super(repo, utils);
    }

    @Override
    protected void setupTree()
        throws TreeRepository.NodeAlreadyAttachedToTree, TreeRepository.NodeNotInTree,
        NoSuchMethodException, InstantiationException, IllegalAccessException,
        InvocationTargetException {
      super.setupTree();
      child2 = repo.createNode("child-2");
      repo.addChild(root, child2);
    }

    @Override
    public String getExpected() {
      // @formatter:off
      return String.format(
          ".\n" +
          "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n" +
          "    ├── child-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/2]\n" +
          "    └── child-2 (id: %d) [treeId: %d | lft: 1/2 | rgt: 3/4]",
          root.getId(), root.getTreeId(),
          child1.getId(), child1.getTreeId(),
          child2.getId(), child2.getTreeId());
      // @formatter:on
    }
  }

  @SuppressWarnings("rawtypes")
  static class TreeWithChildAndSubChild<T extends TreeEntity> extends TreeWithOneChild<T> {
    public T subChild1;

    public TreeWithChildAndSubChild(TreeRepository<T> repo, TreeUtils<T> utils) {
      super(repo, utils);
    }

    @Override
    protected void setupTree()
        throws TreeRepository.NodeAlreadyAttachedToTree, TreeRepository.NodeNotInTree,
        NoSuchMethodException, InstantiationException, IllegalAccessException,
        InvocationTargetException {
      super.setupTree();
      subChild1 = repo.createNode("subChild-1");
      repo.addChild(child1, subChild1);
    }

    @Override
    public String getExpected() {
      // @formatter:off
      return String.format(
          ".\n" +
          "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n" +
          "    └── child-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/2]\n" +
          "        └── subChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/4]",
          root.getId(), root.getTreeId(),
          child1.getId(), child1.getTreeId(),
          subChild1.getId(), subChild1.getTreeId());
      // @formatter:on
    }
  }

  @SuppressWarnings("rawtypes")
  static class ComplexTree1<T extends TreeEntity> extends TreeWithTwoChildren<T> {
    public T subChild1;

    public ComplexTree1(TreeRepository<T> repo, TreeUtils<T> utils) {
      super(repo, utils);
    }

    @Override
    protected void setupTree()
        throws TreeRepository.NodeAlreadyAttachedToTree, TreeRepository.NodeNotInTree,
        InvocationTargetException, NoSuchMethodException, InstantiationException,
        IllegalAccessException {
      super.setupTree();
      subChild1 = repo.createNode("subChild-1");
      repo.addChild(child1, subChild1);
    }

    @Override
    public String getExpected() {
      // @formatter:off
      return String.format(
          ".\n" +
          "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n" +
          "    ├── child-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/2]\n" +
          "    │   └── subChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/4]\n" +
          "    └── child-2 (id: %d) [treeId: %d | lft: 1/2 | rgt: 3/4]",
          root.getId(), root.getTreeId(),
          child1.getId(), child1.getTreeId(),
          subChild1.getId(), subChild1.getTreeId(),
          child2.getId(), child2.getTreeId());
      // @formatter:on
    }
  }

  @SuppressWarnings("rawtypes")
  static class ComplexTree2<T extends TreeEntity> extends ComplexTree1<T> {
    public T subSubChild1;

    public ComplexTree2(TreeRepository<T> repo, TreeUtils<T> utils) {
      super(repo, utils);
    }

    @Override
    protected void setupTree()
        throws TreeRepository.NodeAlreadyAttachedToTree, TreeRepository.NodeNotInTree,
        NoSuchMethodException, InstantiationException, IllegalAccessException,
        InvocationTargetException {
      super.setupTree();
      subSubChild1 = repo.createNode("subSubChild-1");
      repo.addChild(subChild1, subSubChild1);
    }

    @Override
    public String getExpected() {
      // @formatter:off
      return String.format(
          ".\n" +
          "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n" +
          "    ├── child-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/2]\n" +
          "    │   └── subChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/4]\n" +
          "    │       └── subSubChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/8]\n" +
          "    └── child-2 (id: %d) [treeId: %d | lft: 1/2 | rgt: 3/4]",
          root.getId(), root.getTreeId(),
          child1.getId(), child1.getTreeId(),
          subChild1.getId(), subChild1.getTreeId(),
          subSubChild1.getId(), subSubChild1.getTreeId(),
          child2.getId(), child2.getTreeId());
      // @formatter:on
    }
  }

  @SuppressWarnings("rawtypes")
  static class ComplexTree3<T extends TreeEntity> extends ComplexTree2<T> {
    public T subChild2;
    public T lastSubChild;

    public ComplexTree3(TreeRepository<T> repo, TreeUtils<T> utils) {
      super(repo, utils);
    }

    @Override
    protected void setupTree()
        throws TreeRepository.NodeAlreadyAttachedToTree, TreeRepository.NodeNotInTree,
        InvocationTargetException, NoSuchMethodException, InstantiationException,
        IllegalAccessException {
      super.setupTree();
      subChild2 = repo.createNode("subChild-2");
      repo.addChild(child1, subChild2);
      lastSubChild = repo.createNode("lastSubChild");
      repo.addChild(child2, lastSubChild);
    }

    @Override
    public String getExpected() {
      // @formatter:off
      return String.format(
          ".\n" +
          "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n" +
          "    ├── child-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/2]\n" +
          "    │   ├── subChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/4]\n" +
          "    │   │   └── subSubChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/8]\n" +
          "    │   └── subChild-2 (id: %d) [treeId: %d | lft: 1/4 | rgt: 3/8]\n" +
          "    └── child-2 (id: %d) [treeId: %d | lft: 1/2 | rgt: 3/4]\n" +
          "        └── lastSubChild (id: %d) [treeId: %d | lft: 1/2 | rgt: 5/8]",
          root.getId(), root.getTreeId(),
          child1.getId(), child1.getTreeId(),
          subChild1.getId(), subChild1.getTreeId(),
          subSubChild1.getId(), subSubChild1.getTreeId(),
          subChild2.getId(), subChild2.getTreeId(),
          child2.getId(), child2.getTreeId(),
          lastSubChild.getId(), lastSubChild.getTreeId());
      // @formatter:on
    }

    public String getExpectedPartial() {
      // @formatter:off
      return String.format(
          ".\n" +
              "└── child-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/2]\n" +
              "    ├── subChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/4]\n" +
              "    │   └── subSubChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/8]\n" +
              "    └── subChild-2 (id: %d) [treeId: %d | lft: 1/4 | rgt: 3/8]",
          child1.getId(), child1.getTreeId(),
          subChild1.getId(), subChild1.getTreeId(),
          subSubChild1.getId(),  subSubChild1.getTreeId(),
          subChild2.getId(), subChild2.getTreeId());
      // @formatter:on
    }

    public String getExpectedAfterChild1Removal() {
      // @formatter:off
      return String.format(
          ".\n" +
              "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n" +
              "    └── child-2 (id: %d) [treeId: %d | lft: 1/2 | rgt: 3/4]\n" +
              "        └── lastSubChild (id: %d) [treeId: %d | lft: 1/2 | rgt: 5/8]",
          root.getId(), root.getTreeId(),
          child2.getId(), child2.getTreeId(),
          lastSubChild.getId(), lastSubChild.getTreeId());
      // @formatter:on
    }

    public String getExpectedAfterChild2Removal() {
      // @formatter:off
      return String.format(
          ".\n" +
              "└── root (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/1]\n" +
              "    └── child-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/2]\n" +
              "        ├── subChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/4]\n" +
              "        │   └── subSubChild-1 (id: %d) [treeId: %d | lft: 0/1 | rgt: 1/8]\n" +
              "        └── subChild-2 (id: %d) [treeId: %d | lft: 1/4 | rgt: 3/8]",
          root.getId(), root.getTreeId(),
          child1.getId(), child1.getTreeId(),
          subChild1.getId(), subChild1.getTreeId(),
          subSubChild1.getId(), subSubChild1.getTreeId(),
          subChild2.getId(), subChild2.getTreeId());
      // @formatter:on
    }
  }
}
