package me.towdium.jecalculation.data.structure;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiPredicate;

import me.towdium.jecalculation.data.label.labels.LOreDict;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;

import org.junit.jupiter.api.BeforeEach;

import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.label.labels.LPlaceholder;
import me.towdium.jecalculation.utils.Utilities;

// @formatter:off

public abstract class AbstractCostListTest {

    protected CostList.Calculator calculator;
    private List<ILabel> inventory = Collections.emptyList();
    private List<Recipe> recipes = new ArrayList<>();

    private List<Utilities.ChatMessage> chatMessages = new ArrayList<>(1);

    private Map<String,List<LPlaceholder>> oreDict = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        Method method = I18n.class.getDeclaredMethod("setLocale", Locale.class);
        method.setAccessible(true);
        method.invoke(null, new Locale());

        CostList.recipeIteratorProvider = new CostList.RecipeIteratorProvider() {
            @Override
            public Iterator<Recipe> recipeIterator() {
                return recipes.iterator();
            }
        };

        CostList.chatMessageSender = new CostList.ChatMessageSender() {
            @Override
            public void sendChatMessage(Utilities.ChatMessage message) {
                chatMessages.add(message);
            }
        };

        ILabel.MERGER.register(
                "placeholder",
                "placeholder",
                ILabel.Impl.form(LPlaceholder.class, LPlaceholder.class, LPlaceholder::merge));
        ILabel.MERGER.register("oreDict", "placeholder", ILabel.Impl.form(LOreDict.class, LPlaceholder.class, new BiPredicate<ILabel, ILabel>() {
            @Override
            public boolean test(ILabel a, ILabel b) {
                if (a instanceof LOreDict && b instanceof LPlaceholder) {
                    LOreDict lod = (LOreDict) a;
                    LPlaceholder lis = (LPlaceholder) b;
                    // TODO check performance
                    if (lod.getAmount() * lis.getAmount() < 0) {
                        for (LPlaceholder placeholder : oreDict.get(lod.getName())) {
                            if (placeholder.matches(lis)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        }));
    }

    protected LPlaceholder placeholder(String name, int count) {
        return new LPlaceholder(name, count);
    }

    protected LOreDict oreDict(String name, int count) {
        return new LOreDict(name, count);
    }

    protected void registerOreDict(String name, LPlaceholder placeholder) {
        oreDict.computeIfAbsent(name, (n) -> new ArrayList<>(1)).add(placeholder);
    }

    protected void inventory(List<ILabel> inventory) {
        this.inventory = inventory;
    }

    protected void recipe(List<ILabel> outputs, List<ILabel> catalysts, List<ILabel> inputs) {
        recipes.add(new Recipe(inputs, catalysts, outputs));
    }

    protected void request(ILabel label) {
        CostList costList = CostList.negative(Arrays.asList(label));
        costList.mergeInplace(CostList.positive(inventory), false);
        calculator = costList.calculate();
    }

    protected void assertInputs(List<ILabel> inputs) {
        List<ILabel> actualInputs = calculator.getInputs();
        if (!inputs.equals(actualInputs)) {
            throw new AssertionError("expectedInputs = " + inputs + ", actualInputs = " + actualInputs);
        }
    }

    protected void assertExcessOutputs(List<ILabel> outputs) {
        List<ILabel> actualOutputs = calculator.getOutputs(inventory);
        if (!outputs.equals(actualOutputs)) {
            throw new AssertionError("expectedOutputs = " + outputs + ", actualOutputs = " + actualOutputs);
        }
    }

    protected void assertCatalysts(List<ILabel> catalysts) {
        List<ILabel> actualCatalysts = calculator.getCatalysts();
        if (!catalysts.equals(actualCatalysts)) {
            throw new AssertionError("expectedCatalysts = " + catalysts + ", actualCatalysts = " + actualCatalysts);
        }
    }

    protected void assertSteps(List<ILabel> steps) {
        List<ILabel> actualSteps = calculator.getSteps();
        if (!steps.equals(actualSteps)) {
            throw new AssertionError("expectedSteps = " + steps + ", actualSteps = " + actualSteps);
        }
    }

    protected void assertChatMessages(List<Utilities.ChatMessage> messages) {
        assert messages.equals(chatMessages);
    }
}
// todo: restore formatter
// @formatter:on