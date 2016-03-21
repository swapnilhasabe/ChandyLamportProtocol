import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;


@SuppressWarnings("serial")
public class Message implements Serializable {
	ProjectMain m = new ProjectMain();
	int n = m.numOfNodes;
}
@SuppressWarnings("serial")
// Application Message consists of just a string and the vector timestamp
class ApplicationMsg extends Message implements Serializable{
	String msg = "hello";
	int nodeId;
	int[] vector;
}
// Marker Msg is used to just send it to its neighbors so just a string
@SuppressWarnings("serial")
class MarkerMsg extends Message implements Serializable{
	String msg = "marker";
	int nodeId;
}
// State message is sent over converge cast tree , state message should have
// the process state and all its incoming channel states 
@SuppressWarnings("serial")
class StateMsg extends Message implements Serializable{
	boolean active;
	int nodeId;
	HashMap<Integer,ArrayList<ApplicationMsg>> channelStates;
	int[] vector;
}
// Finish message is sent by Node 0 to all the other nodes in the system
// to bring the entire system to halt and write to output console
@SuppressWarnings("serial")
class FinishMsg extends Message implements Serializable{
	String msg = "halt";
}