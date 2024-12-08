package me.towdium.jecalculation.data.structure;

import static me.towdium.jecalculation.utils.Utilities.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import me.towdium.jecalculation.polyfill.MethodsReturnNonnullByDefault;
import me.towdium.jecalculation.utils.Utilities;
import me.towdium.jecalculation.utils.wrappers.Pair;

// positive => generate; negative => require
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public abstract class AbstractCostListService<LabelT, RecipeT, CostListT> {

    private final Dependencies<LabelT, RecipeT> d;
    private final CostLists<LabelT, CostListT> costLists;
    private final Class<CostListT> costListClass;

    AbstractCostListService(Dependencies<LabelT, RecipeT> dependencies, CostLists<LabelT, CostListT> costLists,
        Class<CostListT> costListClass) {
        this.d = dependencies;
        this.costLists = costLists;
        this.costListClass = costListClass;
    }

    public CostListT newNegatedCostList(List<LabelT> labels) {
        List<LabelT> negativeLabels = labels.stream()
            .filter(d::isNotEmptyLabel)
            .map(i -> d.multiplyLabel(d.copyLabel(i), -1))
            .collect(Collectors.toList());
        return costLists.newCostList(negativeLabels);
    }

    public CostListT newPosNegCostList(List<LabelT> positive, List<LabelT> negative) {
        CostListT ret = newNegatedCostList(positive);
        multiply(ret, -1);
        mergeInplace(ret, newNegatedCostList(negative), false);
        return ret;
    }

    public CostListT strictMergeCostList(CostListT a, CostListT b) {
        return mergeCostLists(a, b, false);
    }

    public List<LabelT> getLabels(CostListT costList) {
        return costLists.getLabels(costList);
    }

    public Calculation<LabelT> calculate(CostListT costList) {
        ArrayList<Pair<CostListT, CostListT>> procedure = new ArrayList<>();
        ArrayList<LabelT> catalysts = new ArrayList<>();

        return new Calculation<LabelT>() {

            private Iterator<RecipeT> iterator = d.recipeIterator();
            private int index;

            {
                HashSet<CostListT> set = new HashSet<>();
                set.add(costList);

                // reset index & iterator
                reset();
                Pair<RecipeT, Long> next = find();
                int count = 0;
                while (next != null) {
                    CostListT original = getCurrent();
                    List<LabelT> outL = d.getRecipeOutput(next.one)
                        .stream()
                        .filter(d::isNotEmptyLabel)
                        .collect(Collectors.toList());
                    CostListT outC = newNegatedCostList(outL);
                    multiply(outC, -next.two);
                    List<LabelT> inL = d.getRecipeInput(next.one)
                        .stream()
                        .filter(d::isNotEmptyLabel)
                        .collect(Collectors.toList());
                    CostListT inC = newNegatedCostList(inL);
                    multiply(inC, next.two);
                    CostListT result = mergeCostLists(original, outC, false);
                    mergeInplace(result, inC, false);
                    if (!set.contains(result)) {
                        set.add(result);
                        procedure.add(new Pair<>(result, outC));
                        addCatalyst(d.getRecipeCatalyst(next.one));
                        reset();
                    }
                    next = find();
                    if (count++ > 1000) {
                        d.addMaxLoopChatMessage();
                        break;
                    }
                }
            }

            @Override
            public List<LabelT> getCatalysts() {
                return catalysts;
            }

            @Override
            public List<LabelT> getInputs() {
                return getLabels(getCurrent()).stream()
                    .filter(i -> d.getLabelAmount(i) < 0)
                    .map(i -> d.multiplyLabel(d.copyLabel(i), -1))
                    .collect(Collectors.toList());
            }

            @Override
            public List<LabelT> getOutputs(List<LabelT> ignore) {
                return getLabels(getCurrent()).stream()
                    .map(i -> d.multiplyLabel(d.copyLabel(i), -1))
                    .map(
                        i -> ignore.stream()
                            .flatMap(j -> stream(d.mergeLabels(i, j)))
                            .findFirst()
                            .orElse(i))
                    .filter(i -> d.isNotEmptyLabel(i) && d.getLabelAmount(i) < 0)
                    .map(i -> d.multiplyLabel(i, -1))
                    .collect(Collectors.toList());
            }

            @Override
            public List<LabelT> getSteps() {
                List<LabelT> ret = procedure.stream()
                    .map(
                        i -> costLists.getLabels(i.two)
                            .get(0))
                    .collect(Collectors.toList());
                Collections.reverse(ret);
                CostListT cl = multiply(newNegatedCostList(ret), -1);
                CostListT temp = newNegatedCostList(new ArrayList<>());
                mergeInplace(temp, cl, false);
                return costLists.getLabels(temp);
            }

            private void reset() {
                index = 0;
                iterator = d.recipeIterator();
            }

            /**
             * Find next recipe and its amount
             *
             * @return pair of the next recipe and its amount
             */
            @Nullable
            private Pair<RecipeT, Long> find() {
                List<LabelT> labels = getLabels(getCurrent());
                for (; index < labels.size(); index++) {
                    LabelT label = labels.get(index);
                    // Only negative label is required to calculate
                    if (d.getLabelAmount(label) >= 0) continue;
                    // Find the recipe for the label.
                    // Reset or not reset the iterator is a question
                    while (iterator.hasNext()) {
                        RecipeT r = iterator.next();
                        if (d.recipeOutputMatches(r, label)
                            .isPresent()) {
                            return new Pair<>(r, d.multiplier(r, label));
                        }
                    }
                    iterator = d.recipeIterator();
                }
                return null;
            }

            private void addCatalyst(List<LabelT> labels) {
                labels.stream()
                    .filter(d::isNotEmptyLabel)
                    .forEach(
                        i -> catalysts.stream()
                            .filter(j -> d.labelMatches(j, i))
                            .findAny()
                            .map(j -> d.setLabelAmount(j, Math.max(d.getLabelAmount(i), d.getLabelAmount(j))))
                            .orElseGet(Utilities.fake(() -> catalysts.add(i))));
            }

            private CostListT getCurrent() {
                return procedure.isEmpty() ? costList : procedure.get(procedure.size() - 1).one;
            }
        };
    };

    interface Dependencies<LabelT, RecipeT> {

        // Labels
        LabelT copyLabel(LabelT label);

        LabelT getEmptyLabel();

        long getLabelAmount(LabelT label);

        boolean isNotEmptyLabel(LabelT label);

        boolean labelMatches(LabelT self, LabelT that);

        Optional<LabelT> mergeLabels(LabelT a, LabelT b);

        LabelT multiplyLabel(LabelT label, float i);

        LabelT setLabelAmount(LabelT label, long amount);

        // Recipes
        List<LabelT> getRecipeCatalyst(RecipeT recipe);

        List<LabelT> getRecipeInput(RecipeT recipe);

        List<LabelT> getRecipeOutput(RecipeT recipe);

        Optional<LabelT> recipeOutputMatches(RecipeT recipe, LabelT label);

        long multiplier(RecipeT recipe, LabelT label);

        Iterator<RecipeT> recipeIterator();

        // Utilities
        void addMaxLoopChatMessage();
    }

    interface CostLists<LabelT, CostListT> {

        CostListT newCostList(List<LabelT> labels);

        List<LabelT> getLabels(CostListT self);

        void setLabels(CostListT self, List<LabelT> labels);
    }

    private CostListT mergeCostLists(CostListT a, CostListT b, boolean strict) {
        CostListT ret = copyCostList(a);
        mergeInplace(ret, b, strict);
        return ret;
    }

    /**
     * Merge self to this
     *
     * @param that   cost list to merge
     * @param strict if true, only merge same label
     */
    private void mergeInplace(CostListT self, CostListT that, boolean strict) {
        List<LabelT> thisLabels = getLabels(self);
        getLabels(that).forEach(i -> thisLabels.add(d.copyLabel(i)));
        for (int i = 0; i < thisLabels.size(); i++) {
            for (int j = i + 1; j < thisLabels.size(); j++) {
                if (strict) {
                    LabelT a = thisLabels.get(i);
                    LabelT b = thisLabels.get(j);
                    if (d.labelMatches(a, b)) {
                        thisLabels.set(i, d.setLabelAmount(a, Math.addExact(d.getLabelAmount(a), d.getLabelAmount(b))));
                        thisLabels.set(j, d.getEmptyLabel());
                    }
                } else {
                    Optional<LabelT> l = d.mergeLabels(thisLabels.get(i), thisLabels.get(j));
                    if (l.isPresent()) {
                        thisLabels.set(i, l.get());
                        thisLabels.set(j, d.getEmptyLabel());
                    }
                }
            }
        }
        costLists.setLabels(
            self,
            thisLabels.stream()
                .filter(d::isNotEmptyLabel)
                .collect(Collectors.toList()));
    }

    private CostListT multiply(CostListT self, long i) {
        costLists.setLabels(
            self,
            costLists.getLabels(self)
                .stream()
                .map(j -> d.multiplyLabel(j, i))
                .collect(Collectors.toList()));
        return self;
    }

    boolean costListEquals(CostListT self, Object obj) {
        if (costListClass.isInstance(obj)) {
            CostListT c = (CostListT) obj;
            CostListT m = multiply(copyCostList(c), -1);
            return getLabels(mergeCostLists(self, m, true)).isEmpty();
        } else return false;
    }

    protected CostListT copyCostList(CostListT from) {
        CostListT ret = newNegatedCostList(Collections.emptyList());
        costLists.setLabels(
            ret,
            costLists.getLabels(from)
                .stream()
                .map(d::copyLabel)
                .collect(Collectors.toList()));
        return ret;
    }
}
