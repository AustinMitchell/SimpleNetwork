package simple.network;

import java.net.*;
import java.util.*;

import java.io.*;

public class Server{
    public static class ServerRejectionException extends Exception {
        public ServerRejectionException(String message) {
            super(message);
        }
    }
    
    public static final String FLAG_SERVER_ACCEPT = "SERVER_ACCEPT";
    public static final String FLAG_SERVER_KILL   = "SERVER_KILL";
    public static final String FLAG_NEW_CLIENT    = "NEW_CLIENT";
    
    // two minutes in milliseconds
    public static final int DEFAULT_TIMEOUT = 120000;
    public static final int PORT            = 5000;
    
    public static interface StateProcess {
        public String serverIteration();
    }
    
    private static class ClientData {
        public Integer      id;
        public String       name;
        public InputThread  in;
        public OutputThread out;
        public Socket       socket;
    }
    
	protected Map<String, StateProcess> stateMap;
	protected Queue<Queue<String>>      commandQueue;
	protected Queue<String>             loadedCommand;
	
	private ServerSocket            serverSocket;
	private ArrayList<ClientData>   clients;
    private int          	        maxClients;
	private String                  updateString;
	private String                  serverState;
	private boolean                 run;
	
	public Server(int port) throws IOException {
		serverSocket = new ServerSocket(port);
		serverSocket.setSoTimeout(1000000);
	}
	
	public void setupServer(String initialState) throws IOException {
		System.out.println("Setting up new server...");
		
		commandQueue  = new LinkedList<Queue<String>>();
		loadedCommand = new LinkedList<String>();
		run           = true;
		maxClients    = 1;
		clients       = new ArrayList<ClientData>();
		setServerState(initialState);;
	}
	
	public String getServerState() {
		return serverState;
	}
	
	public int getCurrentNumClients() {
		return clients.size();
	}
	
	public int getMaxClients() {
		return maxClients;
	}
	
	public int getClientLocalPort(int player) {
		return clients.get(player).socket.getLocalPort();
	}
	
	public InetAddress getClientLocalInetAddress(int player) {
		return clients.get(player).socket.getLocalAddress();
	}
	
	public int getClientPort(int player) {
		return clients.get(player).socket.getPort();
	}
	
	public InetAddress getClientInetAddress(int player) {
		return clients.get(player).socket.getInetAddress();
	}
	
	public void setMaxClients(int maxClients) throws IndexOutOfBoundsException {
		this.maxClients = maxClients;
	}
	
	public void setServerState(String stateKey) { 
	    if (!stateMap.containsKey(stateKey)) {
            throw new RuntimeException("Attempted to change state to a stateKey that doesn't exist: " + stateKey);
        }
	    this.serverState = stateKey;
	}
	
	public InetAddress acceptClient() {
		InetAddress result;
		try {
		    ClientData clientData  = new ClientData();
		    
			Socket       socket    = serverSocket.accept();
			InputThread  clientIn  = new InputThread(new BufferedReader (new InputStreamReader(socket.getInputStream())));
			OutputThread clientOut = new OutputThread (new PrintWriter(socket.getOutputStream(), true));
			
			clientData.socket = socket;
			clientData.in     = clientIn;
			clientData.out    = clientOut;
			clientData.id     = clients.size();
			
			// Tell the client you accept them
			clientOut.sendMessage(Server.FLAG_SERVER_ACCEPT);
			
			// Wait for the client's reponse
			try {
			    clientIn.waitForMessage(Server.DEFAULT_TIMEOUT);
                Parser.acceptRawCommands(commandQueue, clientIn.readMessage());
                loadCommand();
			} catch (Exception e) {
				throw new RuntimeException("Input thread has died.");
			}
			// Make sure the client responded with a positive connection
			if (!loadedCommand.poll().equals(Client.FLAG_CLIENT_CONNECT)) {
				clientIn.killThread();
				clientOut.killThread();
				throw new IOException("Client disconnected");
			}
			
			// Wait for another message
			try {
                clientIn.waitForMessage(Server.DEFAULT_TIMEOUT);
                Parser.acceptRawCommands(commandQueue, clientIn.readMessage());
                loadCommand();
            } catch (Exception e) {
                throw new RuntimeException("Input thread has died.");
            }
			// Ensure the client is sending you client info
			if (!loadedCommand.poll().equals(Client.FLAG_CLIENT_INFO)) {
                clientIn.killThread();
                clientOut.killThread();
                throw new IOException("Unexpected message format from Client");
            }
			
			clientData.name = loadedCommand.poll();
			if (clientData.name == null) {
			    clientIn.killThread();
                clientOut.killThread();
                throw new IOException("Client did not send info");
			}
			
			updateAll(Parser.createRawCommand(Server.FLAG_NEW_CLIENT, ""+clientData.id, ""+maxClients, clientData.name));
			
            System.out.println("Player connected: " + clientData.name);
            result = socket.getInetAddress();
            return result;
		}
		catch(SocketTimeoutException s) {
            System.out.println("Socket timed out!");
		}
		catch(IOException e) {
            e.printStackTrace();
		}
		return null;
		
	}

	protected void loadCommand() {
	    loadedCommand = commandQueue.poll();
	}
	
	private void delay() {
	    try { 
	        Thread.sleep(10); 
	    } catch (InterruptedException e) { 
	        e.printStackTrace();
	    }
	}
	public Object[] pollForUpdate(ClientData client) {
        Object[] update = null;
        try {
            if (client.in.hasMessage()) {
                update = new Object[2];
                update[0] = client.id;
                update[1] = client.in.readMessage();
                System.out.println("Message from client " + client.id +" (" + client.name + "): " + (String)update[1]);
            }
        } catch (Exception e) {
            // TODO: Don't fuck everything over when a client dies
            killServer();
            throw new RuntimeException("Input thread has died.");
        }
        
        return update;
    }
	public Object[] waitForUpdateAny() {
		Object[] update = null;
		while(update == null) {
		    delay();
			for (ClientData cl: clients) {
			    update = pollForUpdate(cl);
			}
			if (update != null) {
			    break;
			}
		}
		return update;
	}

	private void updateAll(String update) throws IOException {
		for(int i = 0; i < clients.size(); i++) {
		    updateClient(update, clients.get(i));
		}
	}
	private void updateClient(String update, ClientData cl) throws IOException {
        System.out.println("Sending to client " + cl.id +" (" + cl.name + "): " + update);
        cl.out.sendMessage(update);
    }
	
	public void addServerState(String stateKey, StateProcess iterateProcess) {
	    if (stateMap.containsKey(stateKey)) {
	        throw new RuntimeException("Attempted to add a stateKey that already exists: " + stateKey);
	    }
	    stateMap.put(stateKey, iterateProcess);
	}
	private void handleState() throws IOException {
	    String nextCommand = stateMap.get(serverState).serverIteration();
	    updateAll(nextCommand);
	}

	public void killServer() {
        run = false;
        for (ClientData cl: clients) {
            cl.out.sendMessage(Server.FLAG_SERVER_KILL);
            cl.out.killThread();
            cl.in.killThread();
        }
    }
	
	public void serverLoop() throws IOException {
		while(run) {
			handleState();
		}
	}
	
    //	public static void main(String[] args) {
    //		Server server = null;
    //		try {
    //			server = new Server(Server.PORT);
    //			while(true) {
    //				server.setupServer();
    //				server.serverLoop();
    //				System.out.println("Restarting server...");
    //			}
    //		} catch (IOException e) {
    //			e.printStackTrace();
    //			if (server != null) {
    //				server.killServer();
    //			}
    //		}
    //		
    //	}
    //	
	
}

