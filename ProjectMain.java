import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;

enum Color { RED,BLUE};
@SuppressWarnings("serial")
public class ProjectMain implements Serializable  {
	static String outputFileName;
	int id;
	int numOfNodes,minPerActive,maxPerActive,minSendDelay,snapshotDelay,maxNumber;
	int totalMessagesSent = 0;
	boolean active=false;
	int[][] adjMatrix;
	int[] vector;
	int[] neighbors;
	boolean blockAppMsg = false;
	Color color = Color.BLUE;
	int logging=0;
	boolean firstTime = true;
	String configurationFileName;
	//ArrayList which holds the nodes part of the distributed system 
	ArrayList<Node> nodes = new ArrayList<Node>();
	//HashMap which has node number as keys and <id,host,port> as value
	HashMap<Integer,Node> store = new HashMap<Integer,Node>();
	// Create all the channels in the beginning and keep it open till the end
	HashMap<Integer,Socket> channels = new HashMap<Integer,Socket>();
	// Create all the output streams associated with each socket 
	HashMap<Integer,ObjectOutputStream> oStream = new HashMap<Integer,ObjectOutputStream>();
	// HashMap which stores ArrayList of messages recorded while the process is red for each channel
	HashMap<Integer,ArrayList<ApplicationMsg>> channelStates;
	// HashMap which stores all incoming channels and boolean received marker message
	HashMap<Integer,Boolean> receivedMarker;
	// HashMap which stores all state messages
	HashMap<Integer,StateMsg> stateMessages;	
	//Used to determine if state message has been received from all the processes in the system
	boolean[] nodesInGraph;
	//Every process stores its state(Vector,ChannelStates and its id) in this StateMsg Object
	StateMsg myState;
	//To hold output Snapshots
	ArrayList<int[]> output = new ArrayList<int[]>();

	//Re-initialize everything that is needed for Chandy Lamport protocol before restarting it
	void initialize(ProjectMain mainObj){
		mainObj.channelStates = new HashMap<Integer,ArrayList<ApplicationMsg>>();
		mainObj.receivedMarker = new HashMap<Integer,Boolean>();
		mainObj.stateMessages = new HashMap<Integer,StateMsg>();	

		Set<Integer> keys = mainObj.channels.keySet();
		//Initialize channelStates hashMap
		for(Integer element : keys){
			ArrayList<ApplicationMsg> arrList = new ArrayList<ApplicationMsg>();
			mainObj.channelStates.put(element, arrList);
		}
		//Initialize boolean hashmap receivedMarker to false
		for(Integer e: mainObj.neighbors){
			mainObj.receivedMarker.put(e,false);
		}
		mainObj.nodesInGraph = new boolean[mainObj.numOfNodes];
		mainObj.myState = new StateMsg();
		mainObj.myState.vector = new int[mainObj.numOfNodes];
	}


	public static void main(String[] args) throws IOException, InterruptedException {
		//Read the values for all variables from the configuration file
		ProjectMain mainObj = ConfigParser.readConfigFile(args[1]);
		// Get the node number of the current Node
		mainObj.id = Integer.parseInt(args[0]);
		int curNode = mainObj.id;
		//Get the configuration file from command line
		mainObj.configurationFileName = args[1];
		ProjectMain.outputFileName = mainObj.configurationFileName.substring(0, mainObj.configurationFileName.lastIndexOf('.'));
		//Build converge cast spanning tree in the beginning
		ConvergeCast.buildSpanningTree(mainObj.adjMatrix);
		// Transfer the collection of nodes from ArrayList to hash map which has node id as key since  
		// we need to get and node as value ,it returns <id,host,port> when queried with node Id.
		for(int i=0;i<mainObj.nodes.size();i++){
			mainObj.store.put(mainObj.nodes.get(i).nodeId, mainObj.nodes.get(i));
		}
		// Get the port number on which this node should listen 
		int serverPort = mainObj.nodes.get(mainObj.id).port;
		// Start server on this node's assigned port
		ServerSocket listener = new ServerSocket(serverPort);
		Thread.sleep(10000);
		//Create channels and keep it till the end
		for(int i=0;i<mainObj.numOfNodes;i++){
			// If the value in adjacency matrix is one for the current Node then its a neighbor
			if(mainObj.adjMatrix[curNode][i] == 1){
												String hostName = mainObj.store.get(i).host;
				//InetAddress hostName = InetAddress.getLocalHost();
				int port = mainObj.store.get(i).port;
												InetAddress address = InetAddress.getByName(hostName);
												Socket client = new Socket(address,port);
				// Get the sockets for all neighbors
				//Socket client = new Socket(hostName,port);
				// Put the neighbor sockets in hash map called channels indexed by their node id's
				mainObj.channels.put(i, client);
				// Get an output stream associated with each socket and put it in a hashmap oStream
				ObjectOutputStream oos = new ObjectOutputStream(client.getOutputStream());
				mainObj.oStream.put(i, oos);		
			}
		}

		//Populate neighbors array 
		Set<Integer> keys = mainObj.channels.keySet();
		mainObj.neighbors = new int[keys.size()];
		int index = 0;
		for(Integer element : keys) mainObj.neighbors[index++] = element.intValue();
		//mainObj.vector is used to maintain the current timestamp of the process
		mainObj.vector = new int[mainObj.numOfNodes];

		//Initialize all the datastructures needed for the node to run the protocols
		mainObj.initialize(mainObj);

		//Initially node 0 is active therefore if this node is 0 then it should be active
		if(curNode == 0){
			mainObj.active = true;
			////System.out.println("Emitted Messages");			
			//Call Chandy Lamport protocol if it is node 0
			new CLThread(mainObj).start();		
			new EmitMessagesThread(mainObj).start();
		}
		try {
			while (true) {
				// This node listens as a Server for the clients requests 
				Socket socket = listener.accept();
				// For every client request start a new thread 
				new ClientThread(socket,mainObj).start();
			}
		}
		finally {
			listener.close();
		}
	}


	void emitMessages() throws InterruptedException{

		// get a random number between minPerActive to maxPerActive to emit that many messages
		int numMsgs = 1;
		int minSendDelay = 0;
		synchronized(this){
			numMsgs = this.getRandomNumber(this.minPerActive,this.maxPerActive);
			// If random number is 0 then since node 0 is the only process active in the beginning it will not start
			// therefore get a bigger random number
			if(numMsgs == 0){
				numMsgs = this.getRandomNumber(this.minPerActive + 1,this.maxPerActive);
			}
			minSendDelay = this.minSendDelay;
		}
		//System.out.println("For Node "+this.id+ "  Random number of messages in range min - max per active is  "+numMsgs);
		// channels hashMap has all neighbors as keys, store them in an array to get random neighbor
		for(int i=0;i<numMsgs;i++){
			synchronized(this){
				//get a random number to index in the neighbors and array and get that neighbor
				int neighborIndex = this.getRandomNumber(0,this.neighbors.length-1);
				int curNeighbor = this.neighbors[neighborIndex];
//				System.out.println("Neighbor chosen is "+curNeighbor);
				if(this.active == true){
					//send application message
					ApplicationMsg m = new ApplicationMsg(); 
					// Code for vector protocol
					this.vector[this.id]++;
					m.vector = new int[this.vector.length];
					System.arraycopy( this.vector, 0, m.vector, 0, this.vector.length );
					m.nodeId = this.id;
					//					System.out.println("Timestamp that is being sent while message is emitted ");
					//					for(int s:m.vector){
					//						System.out.println(s+" ");
					//					}
					// Write the message in the channel connecting to neighbor
					try {
						ObjectOutputStream oos = this.oStream.get(curNeighbor);
						oos.writeObject(m);	
						oos.flush();
					} catch (IOException e) {
						e.printStackTrace();
					}	
					//increment totalMessagesSent
					totalMessagesSent++;
				}
			}
			// Wait for minimum sending delay before sending another message
			try {
				Thread.sleep(minSendDelay);
			} catch (InterruptedException e) {
				System.out.println("Error in EmitMessages");
			}
		}
		synchronized(this){
			// After sending minPerActive to maxPerActive number of messages become passive
			this.active = false;
		}

	}

	// Function to generate random number in a given range
	int getRandomNumber(int min,int max){
		// Usually this can be a field rather than a method variable
		Random rand = new Random();
		// nextInt is normally exclusive of the top value,
		// so add 1 to make it inclusive
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}
}

//Server reading objects sent by other clients in the system in a thread 
class ClientThread extends Thread {
	Socket cSocket;
	ProjectMain mainObj;

	public ClientThread(Socket csocket,ProjectMain mainObj) {
		this.cSocket = csocket;
		this.mainObj = mainObj;
	}

	public void run() {
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(cSocket.getInputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		while(true){
			try {
				Message msg;
				msg = (Message) ois.readObject();
				// Synchronizing mainObj so that multiple threads access mainObj in a synchronized way
				synchronized(mainObj){

					//If message is a marker message then process has to turn red if its blue and send messages along all its
					//channels
					if(msg instanceof MarkerMsg){
						int channelNo = ((MarkerMsg) msg).nodeId;
						ChandyLamport.sendMarkerMessage(mainObj,channelNo);
					}	

					//A passive process on receiving an application message only becomes active if 
					//it has sent fewer than maxNumber messages
					else if((mainObj.active == false) && msg instanceof ApplicationMsg && 
							mainObj.totalMessagesSent < mainObj.maxNumber && mainObj.logging == 0){
						mainObj.active = true; 
						new EmitMessagesThread(mainObj).start();
					}
					//If its an application message and logging = 1 then save it
					else if((mainObj.active == false) && (msg instanceof ApplicationMsg) && (mainObj.logging == 1)){
						//Save the channel No from where the message came from
						int channelNo = ((ApplicationMsg) msg).nodeId;
						//Log the application message since logging is enabled
						ChandyLamport.logMessage(channelNo,((ApplicationMsg) msg) ,mainObj);
					}

					//If message is a state message then if this node id is 0 then process it 
					// otherwise forward it to the parent on converge cast tree towards Node 0
					else if(msg instanceof StateMsg){
						if(mainObj.id == 0){
							//System.out.println("Received State msg at Node 0 from node "+((StateMsg)msg).nodeId);
							mainObj.stateMessages.put(((StateMsg)msg).nodeId,((StateMsg)msg));
							mainObj.nodesInGraph[((StateMsg) msg).nodeId] = true;
							//System.out.println("statemessages size = "+mainObj.stateMessages.size());
							if(mainObj.stateMessages.size() == mainObj.numOfNodes){
								//System.out.println("State messages are received at node 0");
								boolean restartChandy = ChandyLamport.processStateMessages(mainObj,((StateMsg)msg));
								if(restartChandy){
									//System.out.println("Restarting Chandy Lamport Protocol");
									mainObj.initialize(mainObj);
									//									for(ArrayList<ApplicationMsg> a:mainObj.channelStates.values()){
									//										System.out.println("Checking if mainObj has empty channel state:"+a.isEmpty());
									//									}
									//Call Chandy Lamport protocol 
									new CLThread(mainObj).start();	
								}								
							}
						}
						else{
							//System.out.println("Forwarding state msg to my parent - node"+mainObj.id);
							ChandyLamport.forwardToParent(mainObj,((StateMsg)msg));
						}
					}
					//If a finishMsg is received then forward the message to all its neighbors
					else if(msg instanceof FinishMsg){	
						//System.out.println("Finish Message of Node "+mainObj.id+" finish message is"+((FinishMsg)msg).msg);
						ChandyLamport.sendFinishMsg(mainObj);
					}

					if(msg instanceof ApplicationMsg){
						//						System.out.println("TimeStamp when application message is received and not processed at node "+mainObj.id);
						//						for(int j: ((ApplicationMsg) msg).vector){
						//							System.out.println(j+" ");
						//						}
						//Code for vector protocol
						for(int i=0;i<mainObj.numOfNodes;i++){
							mainObj.vector[i] = Math.max(mainObj.vector[i], ((ApplicationMsg) msg).vector[i]);
						}
						mainObj.vector[mainObj.id]++;
						// print the vector 
						//						System.out.println("vector of node id "+mainObj.id+" when appln msg is received and processed");
						//						for(int i:mainObj.vector){
						//							System.out.println(i);
						//						}
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

//Thread to start chandy lamport protocol
class CLThread extends Thread{

	ProjectMain mainObj;
	public CLThread(ProjectMain mainObj){
		this.mainObj = mainObj;
	}
	public void run(){
		//If its the first time calling chandy Lamport protocol, start immediately
		if(mainObj.firstTime){
			mainObj.firstTime = false;
		}
		//If its not first time , start after the snapShot delay
		else{
			try {
				Thread.sleep(mainObj.snapshotDelay);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//Irrespective of first or second time we start the protocol if this thread is started
		ChandyLamport.startSnapshotProtocol(mainObj);
	}
}

//Thread to start chandy lamport protocol
class EmitMessagesThread extends Thread{

	ProjectMain mainObj;
	public EmitMessagesThread(ProjectMain mainObj){
		this.mainObj = mainObj;
	}
	public void run(){
		try {
			mainObj.emitMessages();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
