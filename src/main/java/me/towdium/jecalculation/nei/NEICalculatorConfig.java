package me.towdium.jecalculation.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import me.towdium.jecalculation.JustEnoughCalculation;
import me.towdium.jecalculation.gui.JecaGui;

public class NEICalculatorConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        System.out.println("loaded nei config");
        String [] recipeType = new String[]{ "crafting", "crafting2x2", "smelting", "fuel", "brewing"};
        for (String ident : recipeType) {
            API.registerGuiOverlay(JecaGui.class, ident);
            API.registerGuiOverlayHandler(JecaGui.class, new JecaOverlayHandler(), ident);
        }
    }

    @Override
    public String getName() {
        return JustEnoughCalculation.Reference.MODNAME;
    }

    @Override
    public String getVersion() {
        return JustEnoughCalculation.Reference.VERSION;
    }
}
