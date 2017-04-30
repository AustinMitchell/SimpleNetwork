package simple.network;

import org.apache.log4j.Logger;

import model.*;

import java.net.*;
import java.util.LinkedList;
import java.util.Queue;
import java.io.*;

import javax.naming.TimeLimitExceededException;

public class Client implements Runnable {
	public static final String NO_MESSAGE = "";
	public static final String CONNECT_PASSED = "pass";
	public static final String CONNECT_FAILED = "fail";
	public static final String CONNECT_REJECT = "reject";
	
	private static final Object IN_LOCK = new Object();
	private static final Object OUT_LOCK = new Object();
	private static final Object GUI_LOCK = new Object();
	
	private static final Logger log = Logger.getLogger("Client");
	
	private GameState game;
	private InputThread in;
	private OutputThread out;
	private int id, connectTimeoutMillis, port;
	private String updateIn;
	
	private boolean firstToConnect;
	
	private String serverName;
	private String username;
	
	private String connectStatus;
		
	public int getID() {
		return id;
	}
	
	public boolean isFirstToConnect() { return firstToConnect; }

	public Client() {
		this(2000, Server.PORT);
	}
	public Client(int port) {
		this(2000, port);
	}
	// constructor
	public Client(int connectTimeoutMillis, int port) {
		this.connectTimeoutMillis = connectTimeoutMillis;
		this.port = port;
		guiFlags = new LinkedList<String>();
		game = new GameState();
		updateIn = "";
		connectStatus = "";
		firstToConnect = false;
	}

	public GameState getGame() {
		synchronized (game) {
			return game;
		}
	}
	

	public void sendMessage(String message) {
		System.out.println("Message to server: " + message);
		synchronized(OUT_LOCK) {
			out.sendMessage(message);
		}
	}

	public boolean waitingForConnection() {
		synchronized(connectStatus) {
			return connectStatus == NO_MESSAGE;
		}
	}
	public boolean connectPassed() {
		synchronized(connectStatus) {
			boolean result = (connectStatus == CONNECT_PASSED);
			connectStatus = NO_MESSAGE;
			return result;
		}
	}
	public String getConnectMessage() { 
		synchronized(connectStatus) {
			return connectStatus;
		}
	}
	
	public void connect(String serverName, String username) {
		this.serverName = serverName;
		this.username = username;
		new Thread(this).start();
	}
	
	public void killClient() {
		synchronized(IN_LOCK) {
			if (in != null) {
				in.killThread();
			}
		}
		synchronized(OUT_LOCK) {
			if (out != null) {
				out.killThread();
			}
		}
	}
	
	@SuppressWarnings("resource")
	@Override
	public void run() {
		try {
			System.out.println("Connecting to " + serverName + " on port "
					+ port);
			Socket socket = new Socket(serverName, port);
			
			System.out.println("Just connected to "
					+ socket.getRemoteSocketAddress());

			in = new InputThread(new BufferedReader(new InputStreamReader(socket.getInputStream())));
			out = new OutputThread(new PrintWriter(socket.getOutputStream(), true));
			
			long startTime = System.currentTimeMillis();
			while(!in.hasMessage()) {
				if (System.currentTimeMillis() - startTime > connectTimeoutMillis) {
					System.out.println("DISCONNECT");
					out.sendMessage(Flag.CLIENT_DISCONNECT);
					Thread.sleep(500);
					throw new TimeLimitExceededException();
				}
			}
			
			// Expecting Flag.SERVER_ACCEPT
			String message = in.readMessage();
			
			out.sendMessage(username);
			
			// Expecting Flag.PLAYER_ID
			while (!in.hasMessage()) {}
			message = in.readMessage();
			id = Integer.parseInt(message.split(":")[1]) - 1;
			guiFlags.add(message);
			
			log.info(id +": " + username);
			
			if (id == 0) {
				firstToConnect = true;
			}

			synchronized(connectStatus) {
				connectStatus = CONNECT_PASSED;
			}

			while (true) {
				Thread.sleep(10);
				synchronized(IN_LOCK) {
					if (!in.hasMessage()) {
						continue;
					}
					
					updateIn = in.readMessage();
				}
				Parser.networkSplitter(updateIn, game);
				synchronized (GUI_LOCK) {
					Parser.guiSplitter(guiFlags, updateIn);
				}
			}
			// socket.close();
		} catch (TimeLimitExceededException e) {
			synchronized(connectStatus) {
				connectStatus = CONNECT_REJECT;
			}
			killClient();
		} catch (Exception e) {
			synchronized(connectStatus) {
				connectStatus = CONNECT_FAILED;
			}
			killClient();
		}
	}

	/*
	 * ==========================================================================
	 * =========== Creating guiFlags using a Queue where the flags will be
	 * stored
	 */

	private Queue<String> guiFlags;

	// check if the flag queue is empty
	// this is used by the GUI to figure out whether there is a need
	// to update the UI or not
	public boolean hasFlags() {
		synchronized (GUI_LOCK) {
			return !guiFlags.isEmpty();
		}
	}

	
	//return guiFlags queue for testing purposes 
	public Queue<String> getGuiFlags() {
		synchronized(GUI_LOCK) {
			return guiFlags;
		}
	}
	
	public String readGuiFlag() {
		synchronized (GUI_LOCK) {
			String flag = guiFlags.remove();
			System.out.println("Message from server: " + flag);
			return flag;
		}
	}
	
	
	/*
	 * End of Flags preparation
	 * ==================================================
	 * ====================================
	 */

}
