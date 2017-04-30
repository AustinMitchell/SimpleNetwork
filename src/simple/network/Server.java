package simple.network;

import java.net.*;
import java.util.*;

import java.io.*;

public class Server{
    public static interface StateProcess {
        public String serverIteration();
    }
    
    private static class ClientData {
        public String       name;
        public InputThread  in;
        public OutputThread out;
        public Socket       socket;
    }
    
	protected Map<String, StateProcess> stateMap;
	
	public static final int         PORT = 5000;
	private ServerSocket            serverSocket;
	private ArrayList<ClientData>   clients;
    private int          	        maxClients;
	private String                  updateString;
	private String                  serverState;
	
	private boolean run;
	
	public Server(int port) throws IOException {
		serverSocket = new ServerSocket(port);
		serverSocket.setSoTimeout(1000000);
	}
	
	public void setupServer() throws IOException {
		System.out.println("Setting up new server...");
		
		run          = true;
		maxClients   = 1;
		clients      = new ArrayList<ClientData>();
		serverState  = "";
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
	
	public InetAddress acceptPlayer() {
		InetAddress result;
		try {
		    ClientData clientData  = new ClientData();
		    
			Socket       socket    = serverSocket.accept();
			InputThread  clientIn  = new InputThread(new BufferedReader (new InputStreamReader(socket.getInputStream())));
			OutputThread clientOut = new OutputThread (new PrintWriter(socket.getOutputStream(), true));
			
			clientData.socket = socket;
			clientData.in     = clientIn;
			clientData.out    = clientOut;
			
			clientOut.sendMessage(Flag.SERVER_ACCEPT);
			
			String name = null;
			try {
				while(!playerIn.hasMessage()) {}
				name = playerIn.readMessage();
			} catch (Exception e) {
				throw new RuntimeException("Input thread has died.");
			}
			
			if (name.equals(Flag.CLIENT_DISCONNECT)) {
				in.remove(in.size()-1);
				out.remove(out.size()-1);
				playerIn.killThread();
				playerOut.killThread();
				throw new IOException("Client disconnected");
			}
			
			updateClients(Flag.PLAYER_ID + ":" + in.size());
			// This info will get sent out later for the first player, in waitForFirstPlayerSetupInfo()
			if (in.size() > 1) {
				updateClients(Flag.MAX_PLAYERS + ":" + maxPlayers);
				updateClients(Flag.CURRENT_NUM_PLAYERS + ":" + in.size());
			}
			
            Player p = new Player(name);
            System.out.println("Player connected: " + name);
            players.add(p);
            sockets.add(pSocket);
            result = getPlayerClientInetAddress(players.size()-1);
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

	public Object[] getUpdate() {
		Object[] update = null;
		try {
			while(update == null) {
				Thread.sleep(10);
				for (int i=0; i<in.size(); i++) {
					if (in.get(i).hasMessage()) {
						update = new Object[2];
						update[0] = i;
						update[1] = in.get(i).readMessage();
						break;
					}
				}
			}
			System.out.println("Message from client " + (int)update[0] + ": " + (String)update[1]);
		} catch (Exception e) {
			killServer();
			throw new RuntimeException("Input thread has died.");
		}
		
		return update;
	}

	public void updateClients(String update) throws IOException {
		System.out.println("Message to clients: " + update);
		for(int i = 0; i < out.size(); i++) {
			log.info("Sending to client " + i +": " + update);
         	out.get(i).sendMessage(update);
		}
	}
	
	// Watis to accept a new player
	public void waitForPlayer() {
		while (acceptPlayer() == null);
		
		if (players.size() == maxPlayers) {
			serverState = ServerState.CREATE_GAME;
		}		
	}
	
	private void handleState() throws IOException {
	    stateMap.get(serverState).serverIteration();
	}

	public void killServer() {
        run = false;
        for (ClientData cl: clients) {
            cl.out.killThread();
            cl.in.killThread();
        }
    }
	
	public void serverLoop() throws IOException {
		while(run) {
			handleState();
		}
	}
	
	public static void main(String[] args) {
		Server server = null;
		try {
			server = new Server(Server.PORT);
			while(true) {
				server.setupServer();
				server.serverLoop();
				System.out.println("Restarting server...");
			}
		} catch (IOException e) {
			e.printStackTrace();
			if (server != null) {
				server.killServer();
			}
		}
		
	}
	
}

