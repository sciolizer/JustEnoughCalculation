package me.towdium.jecalculation.data.structure;

import java.util.Arrays;
import java.util.Collections;

import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.label.labels.LPlaceholder;
import org.junit.jupiter.api.Test;

import me.towdium.jecalculation.utils.Utilities;

// @formatter:off
public class CostListTest extends AbstractCostListTest {

    @Test
    void oneCobblestone() {
        request(placeholder("cobblestone", 1));
        assertInputs(Arrays.asList(placeholder("cobblestone", 1)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Collections.emptyList());
        assertSteps(Collections.emptyList());
    }

    @Test
    public void threeStone() {
        recipe(Arrays.asList(placeholder("stone", 1)), Arrays.asList(placeholder("furnace", 1)), Arrays.asList(placeholder("cobblestone", 1)));
        inventory(Arrays.asList(placeholder("stone", 1)));
        request(placeholder("stone", 3));
        assertInputs(Arrays.asList(placeholder("cobblestone", 2)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(placeholder("furnace", 1)));
        assertSteps(Arrays.asList(placeholder("stone", 2)));
    }

    @Test
    void tntPure() {
        recipe(Arrays.asList(placeholder("tnt", 1)), Arrays.asList(placeholder("crafting-table", 1)), Arrays.asList(placeholder("gunpowder", 5), placeholder("sand", 4)));
        request(placeholder("tnt", 1));
        assertInputs(Arrays.asList(placeholder("gunpowder", 5), placeholder("sand", 4)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(placeholder("crafting-table", 1)));
        assertSteps(Arrays.asList(placeholder("tnt", 1)));
    }

    @Test
    void tntPartialInventory() {
        recipe(Arrays.asList(placeholder("tnt", 1)), Arrays.asList(placeholder("crafting-table", 1)), Arrays.asList(placeholder("gunpowder", 5), placeholder("sand", 4)));
        inventory(Arrays.asList(placeholder("sand", 1)));
        request(placeholder("tnt", 1));

        // The reason the sand is listed before the gunpowder, instead of the order it is in the recipe, is because adding
        // 3 sand to your inventory when you've already got one won't increase the size of your inventory, while
        // adding 5 gunpowder when you have none actually will.
        assertInputs(Arrays.asList(placeholder("sand", 3), placeholder("gunpowder", 5)));

        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(placeholder("crafting-table", 1)));
        assertSteps(Arrays.asList(placeholder("tnt", 1)));
    }

    @Test
    void minimalInventory1() {
        // This request requires 14 iron blocks and 1000 stone.
        // We should make the iron blocks before making the stone, to minimize the amount of inventory space we take up.
        recipe(Arrays.asList(placeholder("iron-block", 1)), Arrays.asList(placeholder("crafting-table", 1)), Arrays.asList(placeholder("iron-ingot", 9)));
        recipe(Arrays.asList(placeholder("stone", 1)), Arrays.asList(placeholder("furnace", 1)), Arrays.asList(placeholder("cobblestone", 1)));
        recipe(Arrays.asList(placeholder("mega-block", 1)), Collections.emptyList(), Arrays.asList(placeholder("stone", 1000), placeholder("iron-block", 14)));
        request(placeholder("mega-block", 1));

        assertInputs(Arrays.asList(placeholder("iron-ingot", 126), placeholder("cobblestone", 1000)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(placeholder("crafting-table", 1), placeholder("furnace", 1)));
        assertSteps(Arrays.asList(placeholder("iron-block", 14), placeholder("stone", 1000), placeholder("mega-block", 1)));
    }

    @Test
    void minimalInventory2() {
        // Identical to minimalInventory1, except that the mega-block recipe requests iron-blocks before stone. The Calculator should choose to make stone before iron-blocks regardless
        // of which order they are specified in the recipe.
        recipe(Arrays.asList(placeholder("iron-block", 1)), Arrays.asList(placeholder("crafting-table", 1)), Arrays.asList(placeholder("iron-ingot", 9)));
        recipe(Arrays.asList(placeholder("stone", 1)), Arrays.asList(placeholder("furnace", 1)), Arrays.asList(placeholder("cobblestone", 1)));
        recipe(Arrays.asList(placeholder("mega-block", 1)), Collections.emptyList(), Arrays.asList(placeholder("iron-block", 14), placeholder("stone", 1000)));
        request(placeholder("mega-block", 1));

        assertInputs(Arrays.asList(placeholder("iron-ingot", 126), placeholder("cobblestone", 1000)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(placeholder("crafting-table", 1), placeholder("furnace", 1)));
        assertSteps(Arrays.asList(placeholder("iron-block", 14), placeholder("stone", 1000), placeholder("mega-block", 1)));
    }

    @Test
    void infiniteLoop() {
        recipe(Arrays.asList(placeholder("stone", 1)), Arrays.asList(placeholder("furnace", 1)), Arrays.asList(placeholder("cobblestone", 1)));
        recipe(Arrays.asList(placeholder("cobblestone", 1)), Arrays.asList(placeholder("hammer", 1)), Arrays.asList(placeholder("stone", 1)));
        request(placeholder("cobblestone", 1));

        assertInputs(Arrays.asList());
        assertExcessOutputs(Arrays.asList());
        assertCatalysts(Arrays.asList(placeholder("furnace", 1), placeholder("hammer", 1)));
        assert calculator.getSteps().size() <= 1000;

        assertChatMessages(Arrays.asList(Utilities.ChatMessage.MAX_LOOP));
    }

    @Test
    void escapeTheLoop() {
        // a test like the infinite loop, but where there's a second option for crafting cobblestone that would break the loop
        recipe(Arrays.asList(placeholder("stone", 1)), Arrays.asList(placeholder("furnace", 1)), Arrays.asList(placeholder("cobblestone", 1)));
        recipe(Arrays.asList(placeholder("cobblestone", 1)), Arrays.asList(placeholder("hammer", 1)), Arrays.asList(placeholder("stone", 1)));
        recipe(Arrays.asList(placeholder("cobblestone", 9)), Arrays.asList(placeholder("crafting-table", 1)), Arrays.asList(placeholder("compressed-cobblestone", 1)));
        request(placeholder("cobblestone", 1));

        assertInputs(Arrays.asList(placeholder("compressed-cobblestone", 1)));
        assertExcessOutputs(Arrays.asList(placeholder("cobblestone", 8)));
        assertCatalysts(Arrays.asList(placeholder("crafting-table", 1), placeholder("furnace", 1), placeholder("hammer", 1)));

        // This is of course not ideal, since it would be best to skip stone entirely, but it's better than making an infinite loop.
        assertSteps(Arrays.asList(placeholder("cobblestone", 9), placeholder("stone", 1), placeholder("cobblestone", 1)));

        assertChatMessages(Arrays.asList());
    }

    @Test
    void surplus() {
        recipe(Arrays.asList(placeholder("motor", 1)), Arrays.asList(placeholder("crafting-table", 1)), Arrays.asList(placeholder("iron-rod", 2), placeholder("magnetic-iron-rod", 1)));
        recipe(Arrays.asList(placeholder("iron-rod", 64), placeholder("iron-dust", 128)), Arrays.asList(placeholder("lathe", 1)), Arrays.asList(placeholder("iron-ingot", 64)));
        recipe(Arrays.asList(placeholder("magnetic-iron-rod", 64)), Arrays.asList(placeholder("magnetizer", 1)), Arrays.asList(placeholder("iron-rod", 64)));

        request(placeholder("motor", 1));

        assertInputs(Arrays.asList(placeholder("iron-ingot", 128)));
        assertExcessOutputs(Arrays.asList(placeholder("iron-dust", 256), placeholder("iron-rod", 62), placeholder("magnetic-iron-rod", 63)));
        assertCatalysts(Arrays.asList(placeholder("lathe", 1), placeholder("magnetizer", 1), placeholder("crafting-table", 1)));
        assertSteps(Arrays.asList(placeholder("iron-rod", 128), placeholder("magnetic-iron-rod", 64), placeholder("motor", 1)));
    }

    @Test
    void sticks() {
        // sticks from plankWood, fulfill plankWood by making oak planks from oak wood
        registerOreDict("plankWood", placeholder("oak-wood-planks", 1));
        recipe(Arrays.asList(placeholder("stick", 4)), Arrays.asList(placeholder("crafting-table", 1)), Arrays.asList(oreDict("plankWood", 2)));
        recipe(Arrays.asList(placeholder("oak-wood-planks", 4)), Arrays.asList(placeholder("crafting-table", 1)), Arrays.asList(placeholder("oak-wood", 1)));

        request(placeholder("stick", 1));

        assertInputs(Arrays.asList(placeholder("oak-wood", 1)));
        assertExcessOutputs(Arrays.asList(placeholder("oak-wood-planks", 2), placeholder("stick", 3)));
        assertCatalysts(Arrays.asList(placeholder("crafting-table", 1)));
        assertSteps(Arrays.asList(placeholder("oak-wood-planks", 4), placeholder("stick", 4)));
    }
}
// todo: restore formatter
// @formatter:on