package Sampler;

import Normaliser.NormalizedSensorState;
import Normaliser.NormalizedValue;
import Reasoner.Reasoner;
import Reasoner.Reasoning;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Sampler {
  private static Logger sampleLogger;
  private static Logger reasonLogger;
  private static Sampler mSampler;
  private final static int SCOPE_SIZE = 4;
  private List<NormalizedSensorState> mHistory;
  private NormalizedSensorState mPrevious;
  private SampleList sampleList = SampleList.getInstance();
  private int mScopeSize = SCOPE_SIZE;
  private List<Pair<Action,Action>> actionsToBeSanitised = new ArrayList<>();
  private RemovalListener<String, Sample> sanitizerListener = removalNotification -> {
    Sample sample = removalNotification.getValue();
    if(removalNotification.getCause() != RemovalCause.EXPIRED && removalNotification.getCause() != RemovalCause.REPLACED){
      //we done goofed
      //something was removed from uncleanSamples due to something else than time expiration
      sampleLogger.log(Level.SEVERE, "Sample were removed from cache for reasons: " + removalNotification.getCause().toString());
      return;
    }
    if(sample == null){
      sampleLogger.log(Level.SEVERE, "Sample from cache were null");//we're fucked
      //value was garbage-collected before removalListener got to it. Yay dynamic garbage collection! just ignore? not much else to do..
      return;
    }

    for (Pair<Action, Action> actions: actionsToBeSanitised){
      if(sample.getActions().contains(actions.getValue0())){
        sample
            .getActions()
            .get
                (sample
                    .getActions()
                    .indexOf
                        (actions
                            .getValue0()
                        )
                )
            .setValTo
                (sample
                    .getActions()
                    .get
                        (sample
                            .getActions()
                            .indexOf
                                (actions
                                    .getValue0()
                                )
                        )
                    .getValFrom()
                );
      }
      if(sample.getActions().contains(actions.getValue1())){
        sample
            .getActions()
            .get
                (sample
                    .getActions()
                    .indexOf
                        (actions
                            .getValue1()
                        )
                )
            .setValFrom
                (sample
                    .getActions()
                    .get
                        (sample
                            .getActions()
                            .indexOf
                                (actions
                                    .getValue1()
                                )
                        )
                    .getValTo()
                );
      }
    }
    sampleList.add(sample);
    return;
  };
  //husk actions vi har fundet, indenfor 5 sekunder, så vi kan tjekke fejl der går på tværs af samples
  private Cache<String, Sample> uncleanSamples = CacheBuilder
      .newBuilder()
      .concurrencyLevel(1)
      .expireAfterWrite(2, TimeUnit.SECONDS)
      .removalListener(sanitizerListener)
      .build();
  private Reasoner reasoner;

  List<NormalizedValue> zeroVal = new ArrayList<NormalizedValue>();

  /**
   * Initializes an object of Sampler class.
   */
  private Sampler(int SCOPE_SIZE, Logger sampleLogger, Logger reasonLogger){
    this.sampleLogger = sampleLogger;
    this.reasonLogger = reasonLogger;
    reasoner = Reasoner.getInstance(reasonLogger);
    mScopeSize = SCOPE_SIZE;
    zeroVal.add(new NormalizedValue(0,false,"NotADevice",0,0));
    List<NormalizedSensorState> history = new ArrayList<NormalizedSensorState>(); //Initialize mHistory

    for (int i = 0; i < mScopeSize; i++) {
      history.add(null);
    }
    mHistory = history;
    mPrevious = null;
  }

  /**
   * Get instance method to ensure singleton pattern,
   *
   * @return the one and only object of the Sampler class.
   */
  public static Sampler getInstance(Logger sampleLogger, Logger reasonLogger) {
    if (mSampler == null) {
      mSampler = new Sampler(SCOPE_SIZE, sampleLogger, reasonLogger);
    }
    return mSampler;
  }

  /**
   * @return A sensor state
   */
  private NormalizedSensorState oldState = null;

  public Sample getSample(NormalizedSensorState newState) {
    if(newState == null){
      sampleLogger.log(Level.SEVERE, "newState is null");
    }
    Sample res = null;
    if(oldState != null && !oldState.equals(newState)){
      List<Action> acs = findActions(mPrevious, newState);
      findInvertedActionsAndCleanStates(new Sample(mHistory, newState.getTime(), acs), newState); //important: The sample here is not the same as the one below, as newState is modified in this method
      List<Action> acsCorrected = findActions(mPrevious, newState); //we find actions again, as newState is modified
      moveScope(newState);
      res = new Sample(mHistory, newState.getTime(), acsCorrected);
      sampleLogger.log(Level.INFO, "Sample:" + res.toString1());
    }
    oldState = newState;
    return res;
  }

  private void moveScope(NormalizedSensorState newState){
    mHistory.remove(0); // Remove eldest entry

    mHistory.add(newState);
    mPrevious = newState;
  }

  /**
   * @return A sensor state
   */
  public List<Action> findActions(NormalizedSensorState state1, NormalizedSensorState state2) {
    if (state1 == null) {
      // return empty list, as we cannot not find a action if the previous or current state was null
      List<Action> acs = findEmulatables(state2).stream()
          .map(nv -> new Action(null, nv, nv.getSensorIndexOnDevice()))
          .collect(Collectors.toList());
      return acs;
    }
    List<NormalizedValue> emulatables1 = findEmulatables(state1);
    List<NormalizedValue> emulatables2 = findEmulatables(state2);
    List<Action> actions = new ArrayList<Action>();
    for (int i = 0; i < emulatables1.size() && i < emulatables2.size(); i++) {//CANT ZIP Shitty java
      // add all emulatable sensor values as actions
      actions.add(new Action(emulatables1.get(i),emulatables2.get(i),emulatables2.get(i).getSensorIndexOnDevice()));
    }
    return actions;
  }


  public List<NormalizedValue> findEmulatables(NormalizedSensorState state) {
    return state.getNormalizesValues()
                .stream()
                .filter(sensorValue -> sensorValue.isEmulatable())
                .collect(Collectors.toList());
  }

  /**
   * Gives the inverse action to the one given
   * @param action the action from which the inverse is wanted
   * @return the inverse action to the one given as input
   */
  public static Action inverseAction(Action action) {
    if(action == null || action.getValFrom() == null || action.getValTo() == null) {return null;}
    if(action.getValFrom().getValue() == action.getValTo().getValue()){
      return null;
    }
    return new Action(action.getValTo(), action.getValFrom(), action.getDevice());
  }

  /**
   * Cleans states, by finding actions which are inverted, and resetting their values to the correct value
   * @param sample the current sample
   * @param state2 the state which correlates to the action
   */
  private void findInvertedActionsAndCleanStates(Sample sample, NormalizedSensorState state2) {
    //region Update found samples cache
    uncleanSamples.put(sample.toString(), sample);
    //endregion

    //region Identify and correct inverted actions
    List<Action> validActions = new ArrayList<Action>();
    for (Sample s: uncleanSamples.asMap().values())
    {
      for (Action action: s.getActions()){
        if(action != null && action.getValFrom() != null && action.getValTo() != null){
          validActions.add(action);
        }
      }
    }

    ConcurrentMap<Action, Reasoning> sentActions = reasoner.getSentActions();
    for (int i = 0; i < validActions.size() - 1; i++) {
      Action actionI = validActions.get(i);
      if(actionI.getValFrom() == null) {
        continue;
      }
      for (int j = i + 1; j < validActions.size(); j++) { //Get all combinations of actions in sample
        Action actionJ = validActions.get(j);
        if(actionJ.getValFrom() == null) {
          continue;
        }
        if (actionI.equals(inverseAction(actionJ))) { //If two actions are inverse to each other
          actionsToBeSanitised.add(new Pair<>(actionI, actionJ));

          Reasoning reasoning = /*reasoner.*/getReasoningBehindAction(actionI, sentActions);
          if(reasoning != null){
            reasoner.updateModel(reasoning);
          }
        }
      }
    }
    //endregion
  }

  public Reasoning getReasoningBehindAction(Action action, ConcurrentMap<Action, Reasoning> sentActions){
    if (sentActions.containsKey(action)){
      return sentActions.get(action);
    }
    return null;
  }
}
