package simple.network;

import java.net.*;
import java.util.LinkedList;
import java.util.Queue;
import java.io.*;

import javax.naming.TimeLimitExceededException;

public class Client implements Runnable {
    public static final String FLAG_CLIENT_CONNECT    = "CLIENT_CONNECT";
    public static final String FLAG_CLIENT_DISCONNECT = "CLIENT_DISCONNECT";
    public static final String FLAG_CLIENT_INFO       = "CLIENT_INFO";
    
	public static final String NO_MESSAGE = "";
	public static final String CONNECT_PASSED = "pass";
	public static final String CONNECT_FAILED = "fail";
	public static final String CONNECT_REJECT = "reject";
	
	private static final Object IN_LOCK = new Object();
	private static final Object OUT_LOCK = new Object();
		
	protected Queue<Queue<String>> commandQueue;
	protected Queue<String>        loadedCommand;
	
	private Parser       commandParser;
	private InputThread  in;
	private OutputThread out;
	
	private int          id;
	private int          connectTimeoutMillis; 
	private int          port;
	
	private String       updateIn;
	private String       serverName;
    private String       username;
    private String       connectStatus;
    
	private boolean      firstToConnect;
	
	
		
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
		this.port                 = port;
		
		updateIn       = "";
		connectStatus  = "";
		firstToConnect = false;
		commandQueue   = new LinkedList<Queue<String>>();
		loadedCommand  = new LinkedList<String>();
	}

	public Parser getCommandParser() { return commandParser; }

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
	
	protected void loadCommand() {
        loadedCommand = commandQueue.poll();
    }
	
	@SuppressWarnings("resource")
	@Override
	public void run() {
		try {
			System.out.println("Connecting to " + serverName + " on port " + port);
			Socket socket = new Socket(serverName, port);
			
			System.out.println("Just connected to " + socket.getRemoteSocketAddress());

			in  = new InputThread(new BufferedReader(new InputStreamReader(socket.getInputStream())));
			out = new OutputThread(new PrintWriter(socket.getOutputStream(), true));
			
			long startTime = System.currentTimeMillis();
			while(!in.hasMessage()) {
				if (System.currentTimeMillis() - startTime > connectTimeoutMillis) {
					System.out.println("DISCONNECT");
					out.sendMessage(FLAG_CLIENT_DISCONNECT);
					Thread.sleep(500);
					throw new TimeLimitExceededException();
				}
			}
			
			// Expecting Server.FLAG_SERVER_ACCEPT
			Parser.acceptRawCommands(commandQueue, in.readMessage());
			loadCommand();
			if (!loadedCommand.poll().equals(Server.FLAG_SERVER_ACCEPT)) {
			    throw new Server.ServerRejectionException("Server rejected the client");
			}
			
			out.sendMessage(Client.FLAG_CLIENT_CONNECT);
			out.sendMessage(Parser.createRawCommand(FLAG_CLIENT_INFO, username));
			
			// Expecting Flag.PLAYER_ID
			in.waitForMessage(0);
			Parser.acceptRawCommands(commandQueue, in.readMessage());
            loadCommand();
            if (!loadedCommand.poll().equals(Server.FLAG_NEW_CLIENT)) {
                throw new Exception("Server sent unexpected message");
            }
            
			id = Integer.parseInt(loadedCommand.poll());
						
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
					
					Parser.acceptRawCommands(commandQueue, in.readMessage());
				}
				loadCommand();
				if (loadedCommand != null) {
				    commandParser.parse(loadedCommand);
				}
			}
			// socket.close();
		} catch (TimeLimitExceededException e) {
			synchronized(connectStatus) {
				connectStatus = CONNECT_REJECT;
			}
			killClient();
		} catch (Server.ServerRejectionException e) {
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
}
