// Object that will have <Identifier> <Hostname> <Port> read from config file stored
public class Node {
	int nodeId;
	String host;
	int port;
	public Node(int nodeId, String host, int port) {
		super();
		this.nodeId = nodeId;
		this.host = host;
		this.port = port;
	}
}