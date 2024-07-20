package me.towdium.jecalculation.data.structure;

import java.util.Arrays;
import java.util.Collections;

import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.label.labels.LItemStack;
import me.towdium.jecalculation.data.label.labels.LOreDict;
import org.junit.jupiter.api.Test;

import me.towdium.jecalculation.utils.Utilities;

// @formatter:off
public class CostListTest extends AbstractCostListTest {

    @Test
    void oneCobblestone() {
        request(label("cobblestone", 1));
        assertInputs(Arrays.asList(label("cobblestone", 1)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Collections.emptyList());
        assertSteps(Collections.emptyList());
    }

    @Test
    public void threeStone() {
        recipe(Arrays.asList(label("stone", 1)), Arrays.asList(label("furnace", 1)), Arrays.asList(label("cobblestone", 1)));
        inventory(Arrays.asList(label("stone", 1)));
        request(label("stone", 3));
        assertInputs(Arrays.asList(label("cobblestone", 2)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(label("furnace", 1)));
        assertSteps(Arrays.asList(label("stone", 2)));
    }

    @Test
    void tntPure() {
        recipe(Arrays.asList(label("tnt", 1)), Arrays.asList(label("crafting-table", 1)), Arrays.asList(label("gunpowder", 5), label("sand", 4)));
        request(label("tnt", 1));
        assertInputs(Arrays.asList(label("gunpowder", 5), label("sand", 4)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(label("crafting-table", 1)));
        assertSteps(Arrays.asList(label("tnt", 1)));
    }

    @Test
    void tntPartialInventory() {
        recipe(Arrays.asList(label("tnt", 1)), Arrays.asList(label("crafting-table", 1)), Arrays.asList(label("gunpowder", 5), label("sand", 4)));
        inventory(Arrays.asList(label("sand", 1)));
        request(label("tnt", 1));

        // The reason the sand is listed before the gunpowder, instead of the order it is in the recipe, is because adding
        // 3 sand to your inventory when you've already got one won't increase the size of your inventory, while
        // adding 5 gunpowder when you have none actually will.
        assertInputs(Arrays.asList(label("sand", 3), label("gunpowder", 5)));

        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(label("crafting-table", 1)));
        assertSteps(Arrays.asList(label("tnt", 1)));
    }

    @Test
    void minimalInventory1() {
        // This request requires 14 iron blocks and 1000 stone.
        // We should make the iron blocks before making the stone, to minimize the amount of inventory space we take up.
        recipe(Arrays.asList(label("iron-block", 1)), Arrays.asList(label("crafting-table", 1)), Arrays.asList(label("iron-ingot", 9)));
        recipe(Arrays.asList(label("stone", 1)), Arrays.asList(label("furnace", 1)), Arrays.asList(label("cobblestone", 1)));
        recipe(Arrays.asList(label("mega-block", 1)), Collections.emptyList(), Arrays.asList(label("stone", 1000), label("iron-block", 14)));
        request(label("mega-block", 1));

        assertInputs(Arrays.asList(label("iron-ingot", 126), label("cobblestone", 1000)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(label("crafting-table", 1), label("furnace", 1)));
        assertSteps(Arrays.asList(label("iron-block", 14), label("stone", 1000), label("mega-block", 1)));
    }

    @Test
    void minimalInventory2() {
        // Identical to minimalInventory1, except that the mega-block recipe requests iron-blocks before stone. The Calculator should choose to make stone before iron-blocks regardless
        // of which order they are specified in the recipe.
        recipe(Arrays.asList(label("iron-block", 1)), Arrays.asList(label("crafting-table", 1)), Arrays.asList(label("iron-ingot", 9)));
        recipe(Arrays.asList(label("stone", 1)), Arrays.asList(label("furnace", 1)), Arrays.asList(label("cobblestone", 1)));
        recipe(Arrays.asList(label("mega-block", 1)), Collections.emptyList(), Arrays.asList(label("iron-block", 14), label("stone", 1000)));
        request(label("mega-block", 1));

        assertInputs(Arrays.asList(label("iron-ingot", 126), label("cobblestone", 1000)));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(Arrays.asList(label("crafting-table", 1), label("furnace", 1)));
        assertSteps(Arrays.asList(label("iron-block", 14), label("stone", 1000), label("mega-block", 1)));
    }

    @Test
    void infiniteLoop() {
        recipe(Arrays.asList(label("stone", 1)), Arrays.asList(label("furnace", 1)), Arrays.asList(label("cobblestone", 1)));
        recipe(Arrays.asList(label("cobblestone", 1)), Arrays.asList(label("hammer", 1)), Arrays.asList(label("stone", 1)));
        request(label("cobblestone", 1));

        assertInputs(Arrays.asList());
        assertExcessOutputs(Arrays.asList());
        assertCatalysts(Arrays.asList(label("furnace", 1), label("hammer", 1)));
        assert calculator.getSteps().size() <= 1000;

        assertChatMessages(Arrays.asList(Utilities.ChatMessage.MAX_LOOP));
    }

    @Test
    void escapeTheLoop() {
        // a test like the infinite loop, but where there's a second option for crafting cobblestone that would break the loop
        recipe(Arrays.asList(label("stone", 1)), Arrays.asList(label("furnace", 1)), Arrays.asList(label("cobblestone", 1)));
        recipe(Arrays.asList(label("cobblestone", 1)), Arrays.asList(label("hammer", 1)), Arrays.asList(label("stone", 1)));
        recipe(Arrays.asList(label("cobblestone", 9)), Arrays.asList(label("crafting-table", 1)), Arrays.asList(label("compressed-cobblestone", 1)));
        request(label("cobblestone", 1));

        assertInputs(Arrays.asList(label("compressed-cobblestone", 1)));
        assertExcessOutputs(Arrays.asList(label("cobblestone", 8)));
        assertCatalysts(Arrays.asList(label("crafting-table", 1), label("furnace", 1), label("hammer", 1)));

        // This is of course not ideal, since it would be best to skip stone entirely, but it's better than making an infinite loop.
        assertSteps(Arrays.asList(label("cobblestone", 9), label("stone", 1), label("cobblestone", 1)));

        assertChatMessages(Arrays.asList());
    }

    @Test
    void surplus() {
        recipe(Arrays.asList(label("motor", 1)), Arrays.asList(label("crafting-table", 1)), Arrays.asList(label("iron-rod", 2), label("magnetic-iron-rod", 1)));
        recipe(Arrays.asList(label("iron-rod", 64), label("iron-dust", 128)), Arrays.asList(label("lathe", 1)), Arrays.asList(label("iron-ingot", 64)));
        recipe(Arrays.asList(label("magnetic-iron-rod", 64)), Arrays.asList(label("magnetizer", 1)), Arrays.asList(label("iron-rod", 64)));

        request(label("motor", 1));

        assertInputs(Arrays.asList(label("iron-ingot", 128)));
        assertExcessOutputs(Arrays.asList(label("iron-dust", 256), label("iron-rod", 62), label("magnetic-iron-rod", 63)));
        assertCatalysts(Arrays.asList(label("lathe", 1), label("magnetizer", 1), label("crafting-table", 1)));
        assertSteps(Arrays.asList(label("iron-rod", 128), label("magnetic-iron-rod", 64), label("motor", 1)));
    }

    @Test
    void sticks() {
        // sticks from plankWood, fulfill plankWood by making oak planks from oak wood
        ILabel plankWood = label("plankWood", 1);
        oreDict("plankWood", plankWood);
        recipe(Arrays.asList(label("stick"), ))
    }
}
// todo: restore formatter
// @formatter:on