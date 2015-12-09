package Normaliser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        for(int i=0;i<normalizesValues.size();i++)
            hash += normalizesValues.get(i).getValue() * 17 * time.hashCode();
        return hash;
    }
}
