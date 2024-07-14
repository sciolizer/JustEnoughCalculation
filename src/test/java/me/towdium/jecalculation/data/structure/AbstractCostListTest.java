package me.towdium.jecalculation.data.structure;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
    }

    protected ILabel label(String name, int count) {
        return new LPlaceholder(name, count);
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
        assert outputs.equals(actualOutputs);
    }

    protected void assertCatalysts(List<ILabel> catalysts) {
        List<ILabel> actualCatalysts = calculator.getCatalysts();
        assert catalysts.equals(actualCatalysts);
    }

    protected void assertSteps(List<ILabel> steps) {
        List<ILabel> actualSteps = calculator.getSteps();
        assert steps.equals(actualSteps);
    }

    protected void assertChatMessages(List<Utilities.ChatMessage> messages) {
        assert messages.equals(chatMessages);
    }
}
// todo: restore formatter
// @formatter:on