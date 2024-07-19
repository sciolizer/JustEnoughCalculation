package me.towdium.jecalculation.data.structure;

import static me.towdium.jecalculation.utils.Utilities.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import me.towdium.jecalculation.Tags;
import me.towdium.jecalculation.data.Controller;
import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.polyfill.MethodsReturnNonnullByDefault;
import me.towdium.jecalculation.utils.Utilities;

// @formatter:off

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CostList {

    private static final Logger logger = LogManager.getLogger(Tags.MODID);

    public static RecipeIteratorProvider recipeIteratorProvider = Controller::recipeIterator;

    public static ChatMessageSender chatMessageSender = Utilities::addChatMessage;

    List<ILabel> labels;

    public CostList() {
        labels = new ArrayList<>();
    }

    public CostList(List<ILabel> labels) {
        this.labels = labels.stream()
            .filter(i -> i != ILabel.EMPTY)
            .map(
                i -> i.copy()
                    .multiply(-1))
            .collect(Collectors.toList());
    }

    public static CostList positive(List<ILabel> labels) {
        return new CostList(labels, Collections.emptyList());
    }

    public static CostList negative(List<ILabel> labels) {
        return new CostList(labels);
    }

    public CostList(List<ILabel> positive, List<ILabel> negative) {
        this(positive);
        multiply(-1);
        mergeInplace(new CostList(negative), false);
    }

    public static CostList merge(CostList a, CostList b, boolean strict) {
        CostList ret = a.copy();
        ret.mergeInplace(b, strict);
        return ret;
    }

    /**
     * Merge that to this
     *
     * @param that   cost list to merge
     * @param strict if true, only merge same label
     */
    public void mergeInplace(CostList that, boolean strict) {
        that.labels.forEach(i -> this.labels.add(i.copy()));
        for (int i = 0; i < this.labels.size(); i++) {
            for (int j = i + 1; j < this.labels.size(); j++) {
                if (strict) {
                    ILabel a = this.labels.get(i);
                    ILabel b = this.labels.get(j);
                    if (a.matches(b)) {
                        this.labels.set(i, a.setAmount(Math.addExact(a.getAmount(), b.getAmount())));
                        this.labels.set(j, ILabel.EMPTY);
                    }
                } else {
                    Optional<ILabel> l = ILabel.MERGER.merge(this.labels.get(i), this.labels.get(j));
                    if (l.isPresent()) {
                        this.labels.set(i, l.get());
                        this.labels.set(j, ILabel.EMPTY);
                    }
                }
            }
        }
        this.labels = this.labels.stream()
            .filter(i -> i != ILabel.EMPTY)
            .collect(Collectors.toList());
    }

    public CostList multiply(long i) {
        labels = labels.stream()
            .map(j -> j.multiply(i))
            .collect(Collectors.toList());
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CostList) {
            CostList c = (CostList) obj;
            CostList m = c.copy()
                .multiply(-1);
            return CostList.merge(this, m, true).labels.isEmpty();
        } else return false;
    }

    public CostList copy() {
        CostList ret = new CostList();
        ret.labels = labels.stream()
            .map(ILabel::copy)
            .collect(Collectors.toList());
        return ret;
    }

    public boolean isEmpty() {
        return labels.isEmpty();
    }

    public List<ILabel> getLabels() {
        return labels;
    }

    public Calculator calculate() {
        return new Calculator();
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (ILabel i : labels) hash ^= i.hashCode();
        return hash;
    }

    @Override
    public String toString() {
        return Arrays.toString(labels.toArray());
    }

    public class Calculator {

        ArrayList<ILabel> catalysts = new ArrayList<>();

        List<ILabel> steps;

        CostList current; // inputs are negative, excess outputs are positive

        // There's a lot going on here, but the core idea is very simple:
        // 1. Build a dependency (directed acyclic) graph.
        // 2. Topologically sort it.
        //
        // There are a couple things we want to achieve:
        // * Batch crafting steps where possible. If 7 different things require stone, don't make 7 crafting steps.
        //   Collapse them down to a single step if possible.
        // * Present the steps in an order that makes sense. Craft stone before crafting stone bricks.
        //
        // We cannot achieve both of these without tracking dependencies between steps. See
        //   https://github.com/Towdium/JustEnoughCalculation/issues/110#issuecomment-2227190859
        //   for an example of why combining the 7 stone crafting steps cannot be done in a post-processing step, though
        //   that would certainly be simpler.
        //
        // Additionally, this algorithm attempts to choose an order of steps that won't fill up the user's inventory too
        // much. If two different items can be crafted together to free up an inventory slot, this algorithm will usually
        // present that step sooner rather than later. For efficiency, such choices are made greedily, so it is not
        // optimal, but seems to work well in practice.
        //
        // The dependency graph has four types of nodes:
        // * a single inventory node, though it might be empty
        // * up to one request node, representing what the user has requested (may be omitted if inventory already has request)
        // * zero or more crafting nodes, each representing a single crafting step (with some multiplier)
        // * zero or more input nodes, each representing something that will be listed in the inputs
        //
        // As the graph changes, each node tracks items it still needs, and items it has in excess.
        //
        // The algorithm in detail:
        //
        // Initialize the graph with the single inventory node and the single request node.
        // While there is at least one node that still needs items:
        //   Connect the node to one with excess of the needed item, if possible.
        //   Otherwise, connect the node to an existing recipe/input node whose multiplier can be increased, if possible.
        //   Otherwise, connect to a new recipe node, if possible.
        //   Otherwise, connect to a new input node.
        //   (Fallback to constructing new nodes if connecting to an existing node would create a loop.)
        // Topologically sort the graph, accumulating inputs, outputs, catalysts, and steps along the way.
        // Also simulate the user's inventory along the way. When there is freedom to choose from multiple nodes to be
        // the next one, greedily choose the one whose simulated inventory would be the smallest.
        public Calculator() throws ArithmeticException {
            logger.info("Calculator::new(): begin");
            List<Node> dependencyGraph = new ArrayList<>();
            Queue<Node> frontier = new LinkedList<>();
            // Initialize dependency graph with 1 (possibly empty) inventory node, and optionally another node for the
            // user request if it is not already met by the inventory
            {
                List<ILabel> inventoryCostList = new ArrayList<>(
                    CostList.this.getLabels()
                        .size());
                List<ILabel> neededs = new ArrayList<>(1);
                for (ILabel label : CostList.this.getLabels()) {
                    if (label.getAmount() > 0) {
                        inventoryCostList.add(label);
                    } else if (label.getAmount() < 0) {
                        neededs.add(label.copy().multiply(-1));
                    }
                }
                dependencyGraph.add(new TheInventoryNode(CostList.positive(inventoryCostList)));
                for (ILabel needed : neededs) { // should never be more than one item, but this will work even if that changes
                    RequestNode requestNode = new RequestNode(needed);
                    dependencyGraph.add(requestNode);
                    frontier.add(requestNode);
                }
            }
            logger.info("Calculator::new(): initial dependencyGraph = " + Arrays.toString(dependencyGraph.toArray()));

            // Fill dependency graph until no nodes need any more items
            {
                List<Node> undepletedNodes = new ArrayList<>(dependencyGraph.size());
                for (Node node : dependencyGraph) {
                    if (!node.isDepleted()) {
                        undepletedNodes.add(node);
                    }
                }

                int count = 0;
                while (count++ < 1000) {
                    Node needer = frontier.poll();
                    if (needer == null) break;
                    if (needer.needed.isEmpty()) continue;
                    logger.info("Calculator::new(): needer (next in frontier) = " + needer);
                    NEEDED_LOOP:
                    while (true) {
                        for (ILabel needed : needer.needed.getLabels()) {
                            Iterable<Node> candidateProviders = skipAncestors(undepletedNodes, needer);
                            // 1. Is there an existing node that has at least 1 item in excess?
                            for (Node candidateProvider : candidateProviders) {
                                for (ILabel excess : candidateProvider.excess.getLabels()) {
                                    if (needed.matches(excess)) {
                                        logger.info("Calculator::new(): before drawing excess, needed = " + needed + ", needer = " + needer + ", provider = " + candidateProvider);
                                        attach(needer, candidateProvider, needed.copy().setAmount(Math.min(needed.getAmount(), excess.getAmount())));
                                        if (candidateProvider.isDepleted()) {
                                            undepletedNodes.remove(candidateProvider);
                                        }
                                        logger.info("Calculator::new(): after drawing excess, needer = " + needer + ", provider = " + candidateProvider);
                                        continue NEEDED_LOOP;
                                    }
                                }
                            }
                            // 2. If not, is there an existing node whose multiplier or count can be increased?
                            for (Node candidateProvider : candidateProviders) {
                                if (candidateProvider.dialUpFor(needed)) {
                                    logger.info("Calculator::new(): before attaching to dialed up, needed = " + needed + ", needer = " + needer + ", provider = " + candidateProvider);
                                    if (!frontier.contains(candidateProvider)) {
                                        frontier.offer(candidateProvider);
                                    }
                                    attach(needer, candidateProvider, needed);
                                    logger.info("Calculator::new(): after drawing excess, needer = " + needer + ", provider = " + candidateProvider);
                                    continue NEEDED_LOOP;
                                }
                            }
                            // 3. If not, create either a new RecipeNode or a new Input node.
                            Node provider = createNodeFor(needed);
                            logger.info("Calculator::new(): before creating new node, needed = " + needed + ", needer = " + needer + ", provider = " + provider);
                            frontier.offer(provider);
                            dependencyGraph.add(provider);
                            attach(needer, provider, needed);
                            if (!provider.isDepleted()) {
                                undepletedNodes.add(provider);
                            }
                            logger.info("Calculator::new(): after creating new node, needer = " + needer + ", provider = " + provider);
                            continue NEEDED_LOOP;
                        }
                        break;
                    }
                }
                if (count >= 1000) {
                    chatMessageSender.sendChatMessage(Utilities.ChatMessage.MAX_LOOP);
                }
            }

            // Toposort
            CostList simulatedInventory = new CostList();
            CostList inputs = new CostList();
            CostList excessOutputs = new CostList();
            steps = new ArrayList<>();

            List<List<Node>> graphOrderedByNumberOfChildren = new ArrayList<>();
            for (Node node : dependencyGraph) {
                int numChildren = node.children.size();
                while (graphOrderedByNumberOfChildren.size() <= numChildren) {
                    graphOrderedByNumberOfChildren.add(new ArrayList<>());
                }
                graphOrderedByNumberOfChildren.get(numChildren).add(node);
            }

            while (!dependencyGraph.isEmpty()) {
                int smallestInventorySize = Integer.MAX_VALUE;
                Node best = null;
                for (Node node : graphOrderedByNumberOfChildren.get(0)) {
                    if (node instanceof TheInventoryNode) {
                        best = node;
                        break;
                    }
                    if (!node.children.isEmpty()) {
                        logger.warn("Calculator::new(): bug in toposort - graphOrderedByNumberOfChildren not properly maintained");
                        break;
                    }
                    CostList newCostList = node.simulateInventory(simulatedInventory);
                    int estimatedInventorySize = 0;
                    for (ILabel label : newCostList.getLabels()) {
                        if (label.getAmount() > 0) {
                            estimatedInventorySize += Math.ceil(label.getAmount() / 64.0); // not correct for snowballs or armor, but good enough for an estimate
                        }
                    }
                    logger.info("Calculator::new(): estimatedInventorySize = " + estimatedInventorySize + ", node = " + node);
                    if (estimatedInventorySize < smallestInventorySize) {
                        smallestInventorySize = estimatedInventorySize;
                        best = node;
                    }
                }
                if (best == null) {
                    logger.warn("Calculator::new(): bug in toposort - possibly a loop in crafting dependency tree?");
                    break;
                }

                final Node node = best;

                simulatedInventory = node.simulateInventory(simulatedInventory);
                if (node instanceof TheInventoryNode) {
                    // no inputs
                    excessOutputs.mergeInplace(node.excess, false);
                    // no steps
                    // no catalysts
                }
                if (node instanceof InputNode) {
                    inputs.mergeInplace(CostList.positive(Collections.singletonList(((InputNode) node).label)), false);
                    // node.excess should always be empty
                    // no steps
                    // no catalysts
                }
                if (node instanceof RecipeNode) {
                    RecipeNode recipeNode = (RecipeNode) node;
                    // no inputs
                    excessOutputs.mergeInplace(node.excess, false);
                    steps.add(recipeNode.getStep());
                    addCatalyst(recipeNode.recipe.getCatalyst());
                }
                if (node instanceof RequestNode) {
                    // no inputs
                    // no excess outputs
                    // no steps
                    // no catalysts
                }
                logger.info("Calculator::new(): 0 children node = " + node + ", current = " + simulatedInventory);
                for (Node parent : node.parents) {
                    int oldNumberOfChildren = parent.children.size();
                    parent.children.remove(node);
                    int newNumberOfChildren = parent.children.size();
                    graphOrderedByNumberOfChildren.get(oldNumberOfChildren).remove(parent);
                    graphOrderedByNumberOfChildren.get(newNumberOfChildren).add(parent);
                }
                dependencyGraph.remove(node);
                graphOrderedByNumberOfChildren.get(0).remove(node);
            }
            current = new CostList(excessOutputs.labels, inputs.labels);

            logger.info("Calculator::new(): end");
        }

        private Node createNodeFor(ILabel needed) {
            Iterator<Recipe> iterator = recipeIteratorProvider.recipeIterator();
            while (iterator.hasNext()) {
                Recipe r = iterator.next();
                if (r.matches(needed)
                    .isPresent()) {
                    return new RecipeNode(r, r.multiplier(needed));
                }
            }
            return new InputNode(needed);
        }

        private void addCatalyst(List<ILabel> labels) {
            labels.stream()
                .filter(i -> i != ILabel.EMPTY)
                .forEach(
                    i -> catalysts.stream()
                        .filter(j -> j.matches(i))
                        .findAny()
                        .map(j -> j.setAmount(Math.max(i.getAmount(), j.getAmount())))
                        .orElseGet(Utilities.fake(() -> catalysts.add(i))));
        }

        private CostList getCurrent() {
            return current;
        }

        public List<ILabel> getCatalysts() {
            return catalysts;
        }

        public List<ILabel> getInputs() {
            return getCurrent().labels.stream()
                .filter(i -> i.getAmount() < 0)
                .map(
                    i -> i.copy()
                        .multiply(-1))
                .collect(Collectors.toList());
        }

        public List<ILabel> getOutputs(List<ILabel> ignore) {
            logger.info("Calculator::getOutputs: ignore = " + Arrays.toString(ignore.toArray()));
            return getCurrent().labels.stream()
                .map(
                    i -> i.copy()
                        .multiply(-1))
                .map(
                    i -> ignore.stream()
                        .flatMap(j -> stream(ILabel.MERGER.merge(i, j)))
                        .findFirst()
                        .orElse(i))
                .filter(i -> i != ILabel.EMPTY && i.getAmount() < 0)
                .map(i -> i.multiply(-1))
                .collect(Collectors.toList());
        }

        public List<ILabel> getSteps() {
            return steps;
        }

        private Iterable<Node> skipAncestors(List<Node> dependencyGraph, Node node) {
            Set<Node> ancestors = new HashSet<>();
            Queue<Node> frontier = new LinkedList<>();
            frontier.offer(node);
            Node current;
            while ((current = frontier.poll()) != null) {
                ancestors.add(current);
                for (Node parent : current.parents) {
                    if (!ancestors.contains(parent)) {
                        frontier.offer(parent);
                    }
                }
            }
            return () -> dependencyGraph.stream().filter(n -> !ancestors.contains(n)).iterator();
        }

        private void attach(Node parent, Node child, ILabel transfer) {
            parent.children.add(child);
            child.parents.add(parent);
            CostList subtraction = CostList.negative(Collections.singletonList(transfer));
            parent.needed.mergeInplace(subtraction, false);
            child.excess.mergeInplace(subtraction, false);
        }

    }


    private static abstract class Node {
        // todo: check if we actually need both of these, children and parents
        // it's possible one of them can just be changed to a count?
        private final Set<Node> children = new HashSet<>();
        private final Set<Node> parents = new HashSet<>(); // todo: rename to children, and children to parents

        final CostList needed;
        final CostList excess;

        private Node(CostList needed, CostList excess) {
            this.needed = needed;
            this.excess = excess;
        }

        boolean isDepleted() {
            return excess.labels.stream().allMatch(l -> l == ILabel.EMPTY || l.getAmount() == 0);
        }

        abstract CostList simulateInventory(CostList simulatedInventory);

        abstract boolean dialUpFor(ILabel needed);

        @Override
        public String toString() {
            return "Node{" +
                "children=" + children.stream().map(Node::shortName).collect(Collectors.joining()) +
                ", parents=" + parents.stream().map(Node::shortName).collect(Collectors.joining()) +
                ", needed=" + needed +
                ", excess=" + excess +
                '}';
        }

        abstract String shortName();
    }

    private static class TheInventoryNode extends Node {
        private final CostList inventory;

        private TheInventoryNode(CostList costList) {
            super(new CostList(), costList.copy());
            this.inventory = costList;
        }

        @Override
        CostList simulateInventory(CostList simulatedInventory) {
            return CostList.merge(simulatedInventory, inventory, false);
        }

        @Override
        boolean dialUpFor(ILabel needed) {
            return false;
        }

        @Override
        public String toString() {
            return shortName() + super.toString();
        }

        @Override
        String shortName() {
            return "TheInventoryNode{" +
                "inventory=" + inventory;
        }
    }

    private static class RecipeNode extends Node {
        private final Recipe recipe;
        private long multiplier;

        private RecipeNode(Recipe recipe, long multiplier) {
            super(CostList.positive(recipe.getInput()).multiply(multiplier), CostList.positive(recipe.getOutput()).multiply(multiplier));
            this.recipe = recipe;
            this.multiplier = multiplier;
        }

        @Override
        boolean isDepleted() {
            // A recipe node can always increase its multiplier, and so is never depleted.
            return false;
        }

        @Override
        CostList simulateInventory(CostList simulatedInventory) {
            CostList ret = CostList.merge(simulatedInventory, CostList.negative(recipe.getInput()).multiply(multiplier), false);
            ret.mergeInplace(CostList.positive(recipe.getOutput()).multiply(multiplier), false);
            return ret;
        }

        @Override
        boolean dialUpFor(ILabel needed) {
            for (ILabel output : recipe.getOutput()) {
                if (needed.matches(output)) {
                    // todo: assert that excess is empty? (at least while testing)
                    long extraMultiplier = recipe.multiplier(needed);
                    logger.info("RecipeNode::dialUpFor(): this = " + this + ", needed = " + needed + ", extraMultiplier = " + extraMultiplier);
                    this.multiplier += extraMultiplier;
                    this.excess.mergeInplace(CostList.positive(recipe.getOutput()).multiply(extraMultiplier), false);
                    this.needed.mergeInplace(CostList.positive(recipe.getInput()).multiply(extraMultiplier), false);
                    logger.info("RecipeNode::dialUpFor(): after change, this = " + this);
                    return true;
                }
            }
            return false;
        }

        ILabel getStep() {
            ILabel label = recipe.getOutput().get(0).copy();
            label.setAmount(label.getAmount() * this.multiplier);
            return label;
        }

        @Override
        public String toString() {
            return shortName() + super.toString();
        }

        @Override
        String shortName() {
            return "RecipeNode{" +
                "recipe=" + recipe.getRep() +
                ", multiplier=" + multiplier +
                "} ";
        }
    }

    private static class InputNode extends Node {
        private final ILabel label;

        private InputNode(ILabel label) {
            super(new CostList(), CostList.positive(Collections.singletonList(label)));
            this.label = label;
        }

        @Override
        boolean isDepleted() {
            // label.amount can just be increased, so this is never depleted.
            return false;
        }

        @Override
        CostList simulateInventory(CostList simulatedInventory) {
            CostList item = CostList.positive(Collections.singletonList(label));
            return CostList.merge(simulatedInventory, item, false);
        }

        @Override
        boolean dialUpFor(ILabel needed) {
            if (needed.matches(label)) {
                logger.info("Input::node(): this = " + this + ", needed = " + needed);
                label.setAmount(Math.addExact(label.getAmount(), needed.getAmount()));
                this.excess.mergeInplace(CostList.positive(Collections.singletonList(needed)), false);
                logger.info("Input::node(): after change, this = " + this);
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return shortName() + super.toString();
        }

        @Override
        String shortName() {
            return "InputNode{" +
                "label=" + label +
                "} ";
        }
    }

    private static class RequestNode extends Node {
        private final ILabel label;

        public RequestNode(ILabel label) {
            super(CostList.positive(Collections.singletonList(label)), new CostList());
            this.label = label;
        }

        @Override
        CostList simulateInventory(CostList simulatedInventory) {
            return simulatedInventory;
        }

        @Override
        boolean dialUpFor(ILabel needed) {
            return false;
        }

        @Override
        String shortName() {
            return "the-request-node";
        }

        @Override
        public String toString() {
            return "RequestNode{" +
                "label=" + label +
                "} " + super.toString();
        }
    }

    // a layer of indirection for the unit tests:

    interface RecipeIteratorProvider {
        Iterator<Recipe> recipeIterator();
    }

    interface ChatMessageSender {
        void sendChatMessage(Utilities.ChatMessage message);
    }
}
// todo: restore formatter

// @formatter:on
