package me.towdium.jecalculation.data.structure;

import static me.towdium.jecalculation.data.structure.TestLbl.lbl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class CostListServiceTest extends AbstractCostListServiceTest {

    @Test
    void oneCobblestone() {
        request(lbl("cobblestone"));
        assertInputs(lbl("cobblestone"));
        assertExcessOutputs();
        assertCatalysts();
        assertSteps();
    }

    @Test
    public void threeStone() {
        recipe(lst(lbl("stone")), lst(lbl("furnace")), lst(lbl("cobblestone")));
        inventory(lbl("stone"));
        request(lbl("stone", 3));
        assertInputs(lbl("cobblestone", 2));
        assertExcessOutputs();
        assertCatalysts(lbl("furnace"));
        assertSteps(lbl("stone", 2));
    }

    @Test
    void tntPure() {
        recipe(Arrays.asList(lbl("tnt")), Arrays.asList(lbl("crafting-table")), Arrays.asList(lbl("gunpowder", 5), lbl("sand", 4)));
        request(lbl("tnt"));
        assertInputs(lbl("gunpowder", 5), lbl("sand", 4));
        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(lbl("crafting-table"));
        assertSteps(lbl("tnt"));
    }

    @Test
    void tntPartialInventory() {
        recipe(Arrays.asList(lbl("tnt")), Arrays.asList(lbl("crafting-table")), Arrays.asList(lbl("gunpowder", 5), lbl("sand", 4)));
        inventory(lbl("sand"));
        request(lbl("tnt"));

        // The reason the sand is listed before the gunpowder, instead of the order it is in the recipe, is because adding
        // 3 sand to your inventory when you've already got one won't increase the size of your inventory, while
        // adding 5 gunpowder when you have none actually will.
        assertInputs(lbl("sand", 3), lbl("gunpowder", 5));

        assertExcessOutputs(Collections.emptyList());
        assertCatalysts(lbl("crafting-table"));
        assertSteps(lbl("tnt"));
    }

    @Test
    void basicLoop() {
        recipe(lst(lbl("stone")), lst(lbl("furnace")), lst(lbl("cobblestone")));
        recipe(lst(lbl("cobblestone")), lst(lbl("hammer")), lst(lbl("stone")));
        request(lbl("cobblestone", 1));

        assertExcessOutputs();
        assert calculation.getSteps().size() <= 2000;
    }

    @Test
    void infiniteLoop() {
        recipe(lst(lbl("stone")), lst(lbl("furnace")), lst(lbl("cobblestone")));
        recipe(lst(lbl("cobblestone", 100)), lst(lbl("hammer")), lst(lbl("stone", 101)));
        request(lbl("cobblestone", 1));

        assertCatalysts(lbl("hammer"), lbl("furnace"));
        assert calculation.getSteps().size() <= 2000;
    }

    @Test
    void surplus() {
        recipe(lst(lbl("motor")), WORKBENCH, lst(lbl("iron-rod", 2), lbl("magnetic-iron-rod")));
        recipe(lst(lbl("iron-rod", 64), lbl("iron-dust", 128)), lst(lbl("lathe")), lst(lbl("iron-ingot", 64)));
        recipe(lst(lbl("magnetic-iron-rod", 64)), lst(lbl("magnetizer")), lst(lbl("iron-rod", 64)));

        request(lbl("motor"));

        assertInputs(lbl("iron-ingot", 128));
        assertExcessOutputs(lbl("iron-rod", 62), lbl("magnetic-iron-rod", 63), lbl("iron-dust", 256));
        assertCatalysts(lbl("crafting-table"), lbl("lathe"), lbl("magnetizer"));
        assertSteps(lbl("iron-rod", 128), lbl("magnetic-iron-rod", 64), lbl("motor"));
    }


    private static List<TestLbl> WORKBENCH = lst(lbl("crafting-table"));
    private static <T> List<T> lst(T... args) {
        return Arrays.asList(args);
    }
}
