package me.towdium.jecalculation.data.structure;

import java.util.List;

public interface Calculation<LabelT> {

    List<LabelT> getCatalysts();

    List<LabelT> getInputs();

    List<LabelT> getOutputs(List<LabelT> ignore);

    List<LabelT> getSteps();
}
