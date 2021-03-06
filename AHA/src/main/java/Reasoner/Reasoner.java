package Reasoner;
import Communication.Communicator;
import Sampler.Action;
import Sampler.Sample;
import Sampler.Sampler;
import Sampler.SampleList;
import com.digi.xbee.api.exceptions.XBeeException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * created by kafuch
 */
public class Reasoner{
  private static Reasoner reasoner;
  private static Logger logger;
  private IModel currentModel = null;
  private Communicator com = null;
  private SampleList sampleList = SampleList.getInstance();
  //husk actions vi har sendt, indenfor 1 sekund, så vi kan tjekke om de actions vi får er bruger eller system
  private Cache<Action, Reasoning> sentActions = CacheBuilder.newBuilder().concurrencyLevel(1).expireAfterWrite(2, TimeUnit.SECONDS).build();

  private Cache<Action, Integer> allActions = CacheBuilder.newBuilder().concurrencyLevel(1).expireAfterWrite(2, TimeUnit.SECONDS).build();

  private Reasoner(Logger reasonLogger){
    logger = reasonLogger;
  }

  public static Reasoner getInstance(Logger reasonLogger){
    if (reasoner == null){
      reasoner = new Reasoner(reasonLogger);
    }
    return reasoner;
  }

  public void setCommunicator(Communicator com){
    this.com = com;

  }

  /**
   * Given a sample, calculate an action and send this to communicator
   *
   * @param sample the sample to reason about
   */
  public void reasonAndSend(Sample sample){
    if (currentModel == null){
      logger.log(Level.WARNING, "Model is not generated yet");
      return;
    }
    Reasoning reasoning = currentModel.CalculateReasoning(sample);
    if (reasoning == null){

      return;
    }
    if (reasoning.getActions().isEmpty()){
      logger.log(Level.INFO, "Reasoning succeeded but contained no actions");
      return;
    }
    List<Action> actions = reasoning.getActions()
        .stream()
        .filter(action -> action != null)
        .filter(action -> action.getValFrom().getValue() != action.getValTo().getValue())
        .filter(action -> !sentActions.asMap().keySet().contains(Sampler.inverseAction(action)))
        .collect(Collectors.toList());

    if (actions == null){

      return;
    }
    for (Action action : actions){
      allActions.put(action, action.hashCode());
    }


    for (Action sampleAction : sample.getActions()){
      allActions.put(sampleAction, sampleAction.hashCode());
    }
    boolean skip = false;
    for (Action action : actions){
      for (Action storedAction : allActions.asMap().keySet()){
        sentActions.put(action, reasoning);
        Action inverseAction = Sampler.inverseAction(storedAction);
        if (action.equals(inverseAction)){
          logger.log(Level.INFO, "Found inverse action: " + action.toString());
          wrongResonings.add(reasoning);
          //currentModel.TakeFeedback(reasoning);
          skip = true;
          break;
        }
      }
      if (skip){
        skip = false;
        continue;
      }
      try{
        logger.log(Level.INFO, "Sending data: " + action.toString());
        com.SendData(action.getValFrom().getDeviceAddress(), action.serialize());
      } catch (XBeeException e){
        //Would probably be a good idea to handle the exception instead of ignoring it...
      }
    }
  }


  /**
   * Gives the reasoning behind the given system action
   *
   * @param action the action to get the reasoning behind
   * @return a reasoning or null, if action was not system action (or time limit has expired)
   */
  public Reasoning getReasoningBehindAction(Action action){
    if (sentActions.asMap().containsKey(action)){
      return sentActions.asMap().get(action);
    }
    return null;
  }

  public ConcurrentMap<Action, Reasoning> getSentActions(){
    return sentActions.asMap();
  }

  /**
   * Updates the reasoners instance of the model s.t. the same mistake will not be made several times in a row
   *
   * @param reasoning the reasoning behind the action, which was a mistake
   */
  public void updateModel(Reasoning reasoning){
    if (currentModel != null){
      currentModel.TakeFeedback(reasoning);
      wrongResonings.add(reasoning);
    }
  }

  public void setCurrentModel(IModel model){
    try{
      currentModel = model;
      for(Reasoning wrongReasoning : wrongResonings){
        currentModel.TakeFeedback(wrongReasoning);
      }
    } catch (ConcurrentModificationException e){
      setCurrentModel(model);
    }
  }

  private List<Reasoning> wrongResonings = new ArrayList<>();
}



//This is just placeholders for now


//public class DataBase {
//  IModel GetModel(){}
//
//  void FlagEntries(List<Action> actions){} //should flag every action in the list as valid in the DB's history
//
//  void FlagModel(Action a1, Action a2){}
//}
//