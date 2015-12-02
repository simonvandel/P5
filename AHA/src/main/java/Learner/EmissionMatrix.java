package Learner;

import Sampler.Sample;
import org.apache.commons.math3.linear.BlockRealMatrix;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by simon on 11/30/15.
 */
public class EmissionMatrix
{
  private BlockRealMatrix matrix;
  private HashMap<Sample, Integer> observationMapping;
  /**
   * Generates an observation matrix K x N (observationVariables x hiddenstates) with uniform distribution
   */
  public EmissionMatrix(int numObservationVariables, int numHiddenStates, List<Sample> samples)
  {
    observationMapping = new HashMap<>(numObservationVariables);
    // We want to map samples (that can contain duplicated samples) to an index of unique samples
    int indexCount = 0;
    for (Sample sample: samples)
    {
      observationMapping.put(sample, indexCount);
      indexCount++;
    }

    double[][] data = new double[numObservationVariables][numHiddenStates];
    double preset = 1/numObservationVariables;
    // initialize the matrix to a uniform distribution
    Arrays.fill(data, preset);
    matrix = new BlockRealMatrix(numObservationVariables, numHiddenStates, data, true);
  }

  public double getNorm()
  {
    return matrix.getNorm();
  }

  public int getNumEmissionVariables()
  {
    return matrix.getRowDimension();
  }

  public double getEntry(int hiddenStateIndex, Sample observation)
  {
    int observationIndex = observationMapping.get(observation);
    return matrix.getEntry(observationIndex, hiddenStateIndex);
  }
}
