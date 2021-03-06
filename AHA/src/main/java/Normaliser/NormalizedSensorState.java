package Normaliser;

import Communication.SensorState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Zobair on 19-11-2015.
 */
public class NormalizedSensorState
{
    private List<NormalizedValue> normalizesValues;
    private Instant time;

    public NormalizedSensorState(Instant time)
    {
        this.normalizesValues = new ArrayList<NormalizedValue>();
        this.time = time;
    }

    public NormalizedSensorState(SensorState sensorState, int range) {
        this.normalizesValues = sensorState
            .getValues().stream()
            .map(x -> new NormalizedValue(x.getValue(), x.isEmulatable(), x.getDeviceAddress(), x.getSensorIndexOnDevice(), range))
            .collect(Collectors.toList());
        this.time = sensorState.getTime();
    }

    /**
     * A accessor method for Normalized values.
     *
     * @return List of NormalizedValue objects.
     */
    public List<NormalizedValue> getNormalizesValues()
    {
        return normalizesValues;
    }

    /**
     * An accessor method for the time of the normalized values.
     *
     * @return time.
     */
    public Instant getTime()
    {
        return time;
    }

    /**
     * this method adds the normalized value into the list.
     *
     * @param n is the normalizedvalue that has to be added into the list.
     * @return true if it was possible to add to the list else false.
     */
    public boolean AddNormalizedValue(NormalizedValue n)
    {
        if (n != null)
        {
            return this.normalizesValues.add(n);
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        int usedValues = 0;
        for(int i=0;i<normalizesValues.size();i++){
            NormalizedValue normalizedValue = normalizesValues.get(i);
            hash += usedValues + normalizedValue.getValue() * Math.pow(normalizedValue.getRange(), (normalizedValue.getSensorIndexOnDevice()));
            usedValues += normalizedValue.getRange();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj){
        if (obj instanceof NormalizedSensorState){
            List<NormalizedValue> inputNormalizedValues = ((NormalizedSensorState) obj).getNormalizesValues();
            if(inputNormalizedValues.size() == normalizesValues.size()){
                for(int i = 0; i < inputNormalizedValues.size(); i++){
                    if (!inputNormalizedValues.get(i).equals(normalizesValues.get(i))){
                        return false;
                    }
                }
                return true;
            }else return false;
        }else return false;
    }
}
