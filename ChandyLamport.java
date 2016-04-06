import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;


public class ChandyLamport { 
    //method where protocol starts 
	public static void startSnapshotProtocol(ProjectMain mainObj) {
		synchronized(mainObj){
			// node 0 calls this method to initiate chandy and lamport protocol
			//nodesInGraph is array which holds the status of receivedStateMessage from all the nodes in the system
			mainObj.nodesInGraph[mainObj.id] = true;
			//It turns red and sends marker messages to all its outgoing channels
			sendMarkerMessage(mainObj,mainObj.id);
		}
	}

	public static void sendMarkerMessage(ProjectMain mainObj, int channelNo){
		// Node which receives marker message turns red ,becomes passive and sends
		// marker messages to all its outgoing channels , starts logging
		synchronized(mainObj){
			if(mainObj.color == Color.BLUE){
//				System.out.println("Received first Marker message from node and color is blue, "
//						+ "will be changed to red  "+channelNo);
				mainObj.receivedMarker.put(channelNo, true);
				mainObj.color = Color.RED;
				mainObj.myState.active = mainObj.active;
				mainObj.myState.vector = mainObj.vector;
				mainObj.myState.nodeId = mainObj.id;
//				System.out.println("Node "+mainObj.id+" is sending the following timestamp to Node 0");
//				for(ArrayList<ApplicationMsg> a:mainObj.channelStates.values()){
//					System.out.println("******Checking if mainObj has empty channel state:"+a.isEmpty());
//				}
//				for(int k:mainObj.myState.vector){
//					System.out.print(k+" ");
//				}
				int[] vectorCopy = new int[mainObj.myState.vector.length];
				for(int i=0;i<vectorCopy.length;i++){
					vectorCopy[i] = mainObj.myState.vector[i]; 
				}
//				synchronized(mainObj.output){
				mainObj.output.add(vectorCopy);
//				}
//				new writeToOutputThread(mainObj).start();
				//logging = 1 demands the process to log application messages after it has become red
				mainObj.logging = 1;
				//Send marker messages to all its neighbors
				for(int i : mainObj.neighbors){
					MarkerMsg m = new MarkerMsg();
//					System.out.println("To Node "+i+" process "+mainObj.id+"  is sending marker messages now");
					m.nodeId = mainObj.id;
					ObjectOutputStream oos = mainObj.oStream.get(i);
					try {
						oos.writeObject(m);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if((mainObj.neighbors.length == 1) && (mainObj.id!=0)){
					int parent = ConvergeCast.getParent(mainObj.id);	
					mainObj.myState.channelStates = mainObj.channelStates;
					mainObj.color = Color.BLUE;
					mainObj.logging = 0;
					// Send channel state to parent 
					ObjectOutputStream oos = mainObj.oStream.get(parent);
//					System.out.println("Sending State Msg  by  "+mainObj.id+" and process state is  "+mainObj.myState.active);
					try {
						oos.writeObject(mainObj.myState);
					} catch (IOException e) {
						e.printStackTrace();
					}
					mainObj.initialize(mainObj);
				}


			}
			//If color of the process is red and a marker message is received on this channel
			else if(mainObj.color == Color.RED){
//				System.out.println("Received a marker message when the color of process "+mainObj.id+" is red");
				// Record that on this channel a marker message was received
				mainObj.receivedMarker.put(channelNo, true);
				int i=0;
				//Check if this node has received marker messages on all its incoming channels
//				System.out.println("Size of the neighbors list is "+mainObj.neighbors.length);
				while(i<mainObj.neighbors.length && mainObj.receivedMarker.get(mainObj.neighbors[i]) == true){
					//System.out.println("Received Marker msg from neighbor "+mainObj.neighbors[i]);
					i++;
				}
				// If this node has received marker messages from all its incoming channels then 
				// send process state to Node 0
				if(i == mainObj.neighbors.length && mainObj.id != 0){
					int parent = ConvergeCast.getParent(mainObj.id);				
//					System.out.println("For node "+mainObj.id + ", all neighbours have sent marker messages.");
					// Record the channelState and process State and which node is sending to node 0 as nodeId
					mainObj.myState.channelStates = mainObj.channelStates;
//					for(ArrayList<ApplicationMsg> a:mainObj.channelStates.values()){
//						System.out.println("Checking if mainObj has empty channel state:"+a.isEmpty());
//					}
					mainObj.color = Color.BLUE;
					mainObj.logging = 0;
					// Send channel state to parent 
					ObjectOutputStream oos = mainObj.oStream.get(parent);
//					System.out.println("Sending State Msg  by  "+mainObj.id+" and process state is  "+mainObj.myState.active);
					try {
						oos.writeObject(mainObj.myState);
					} catch (IOException e) {
						e.printStackTrace();
					}
					mainObj.initialize(mainObj);
				}
				if(i == mainObj.neighbors.length &&  mainObj.id == 0){
//					System.out.println("For node 0, all neighbours have sent marker messages.");
					mainObj.myState.channelStates = mainObj.channelStates;
					mainObj.stateMessages.put(mainObj.id, mainObj.myState);
					mainObj.color = Color.BLUE;
					mainObj.logging = 0;
				}
//				if(i != mainObj.neighbors.length){
//					System.out.println("For node "+mainObj.id + ", neighbor " + mainObj.neighbors[i] 
//							+" has not yet sent a marker message.");
//				}
//				

			}
		}
	}

	// This method is called only by node 0 
	public static boolean processStateMessages(ProjectMain mainObj, StateMsg msg) throws InterruptedException {
		int i=0,j=0,k=0;
		synchronized(mainObj){
			// Check if node 0 has received state message from all the nodes in the graph
			while(i<mainObj.nodesInGraph.length && mainObj.nodesInGraph[i] == true){
				i++;
			}
			//If it has received all the state messages 
			if(i == mainObj.nodesInGraph.length){
				//Go through each state message
				for(j=0;j<mainObj.stateMessages.size();j++){
					// Check if any process is still active , if so then no further check required 
					//wait for snapshot delay and restart snapshot protocol
					if(mainObj.stateMessages.get(j).active == true){
//						System.out.println(" *****************Process is still active ");
						return true;
					}
				}
				//If all processes are passive then j is now equal to numOfNodes 
				if(j == mainObj.numOfNodes){
					//now check for channels 
					for(k=0;k<mainObj.numOfNodes;k++){
						// If any process has non-empty channel,  then wait for snapshot 
						// delay and restart snapshot protocol
						StateMsg value = mainObj.stateMessages.get(k);
						for(ArrayList<ApplicationMsg> g:value.channelStates.values()){
							if(!g.isEmpty()){
//								System.out.println("************** Channels are not empty "+k);
//								for(ApplicationMsg m:g)
//									System.out.println(m.nodeId);
								//If channels are not empty immediately return, restart CL protocol is true
								return true;
							}
						}
					}
				}
				//If the above check has passed then it means all channels are empty and all processes are 
				//passive and now node 0 can announce termination - it can a send finish message to all its neighbors
				if(k == mainObj.numOfNodes){
//					System.out.println("Node 0 is sending finish message since all processes are passive and channels empty");					
					sendFinishMsg(mainObj);
					return false;
				}
			}
		}
		return false;
	}


	//When logging is enabled save all the application messages sent on each channel
	//Array list holds the application messages received on each channel
	public static void logMessage(int channelNo,ApplicationMsg m, ProjectMain mainObj) {
		synchronized(mainObj){
			// if the ArrayList is already there just add this message to it 
			if(!(mainObj.channelStates.get(channelNo).isEmpty()) && mainObj.receivedMarker.get(channelNo) != true){
				mainObj.channelStates.get(channelNo).add(m);
			}
			// or create a list and add the message into it
			else if((mainObj.channelStates.get(channelNo).isEmpty()) && mainObj.receivedMarker.get(channelNo) != true){
				ArrayList<ApplicationMsg> msgs = mainObj.channelStates.get(channelNo);
				msgs.add(m);
				mainObj.channelStates.put(channelNo, msgs);
			}
		}
	}

	// A process received a state msg on its channel and the process is not Node 0
	// therefore simply forward it over converge cast tree towards Node 0
	public static void forwardToParent(ProjectMain mainObj, StateMsg stateMsg) {
		synchronized(mainObj){
			int parent = ConvergeCast.getParent(mainObj.id);
			// Send stateMsg to the parent
			ObjectOutputStream oos = mainObj.oStream.get(parent);
			try {
				oos.writeObject(stateMsg);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	//Method to send finish message to all the neighbors of the current Node
	public static void sendFinishMsg(ProjectMain mainObj) {
		synchronized(mainObj){
			new OutputWriter(mainObj).writeToFile();
			for(int s : mainObj.neighbors){
				FinishMsg m = new FinishMsg();
				ObjectOutputStream oos = mainObj.oStream.get(s);
				try {
					oos.writeObject(m);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}			
			System.exit(0);
		}
	}
}

//Print the output to the output File
class OutputWriter {
	ProjectMain mainObj;

	public OutputWriter(ProjectMain mainObj) {
		this.mainObj = mainObj;
	}


	public void writeToFile() {
		String fileName = ProjectMain.outputFileName+"-"+mainObj.id+".out";
		synchronized(mainObj.output){
			try {
				File file = new File(fileName);
				FileWriter fileWriter;
				if(file.exists()){
					fileWriter = new FileWriter(file,true);
				}
				else
				{
					fileWriter = new FileWriter(file);
				}
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
				/*if(file.length()!=0){
                bufferedWriter.write("\n");
            }*/
   
				for(int i=0;i<mainObj.output.size();i++){
					for(int j:mainObj.output.get(i)){
						bufferedWriter.write(j+" ");
						
					}
					if(i<(mainObj.output.size()-1)){
	            bufferedWriter.write("\n");
					}
				}			
				mainObj.output.clear();
				// Always close files.
				bufferedWriter.close();
			}
			catch(IOException ex) {
				System.out.println("Error writing to file '" + fileName + "'");
				// Or we could just do this: ex.printStackTrace();
			}
		}
	}

}
