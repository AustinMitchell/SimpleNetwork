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
		
	protected Queue<String>        loadedCommand;
	
	private PrintStream  logStream;
	
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
		
		logStream      = System.out;
		updateIn       = "";
		connectStatus  = "";
		firstToConnect = false;
		loadedCommand  = new LinkedList<String>();
	}

	public Parser getCommandParser() { return commandParser; }

	public void sendMessage(String...messageArgs) {
	    String msg = Parser.createRawCommand(messageArgs);
		System.out.println("CLIENT: Message to server: " + msg);
		synchronized(OUT_LOCK) {
			out.sendMessage(msg);
		}
	}

	public boolean waitingForConnection() {
		synchronized(connectStatus) {
			return connectStatus == NO_MESSAGE;
		}
	}
	public boolean connectPassed() {
		synchronized(connectStatus) {
			return (connectStatus == CONNECT_PASSED);
		}
	}
	public String getConnectMessage() { 
		synchronized(connectStatus) {
			return connectStatus;
		}
	}
	
	public void setLogStream(PrintStream logStream) {
	    this.logStream = logStream;
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
	
	protected void loadCommand() throws Exception {
        loadedCommand = in.readMessage();
    }
	
	public void update() {
	    if (!connectPassed()) {
	        return;
	    }
	    
	    try {
	        loadCommand();
            if (loadedCommand != null) {
                commandParser.parse(loadedCommand);
            }
        } catch (Exception e) {
            synchronized(connectStatus) {
                connectStatus = CONNECT_FAILED;
            }
            killClient();
        }
	}
	
	@SuppressWarnings("resource")
	@Override
	public void run() {
	    log("Starting run()...");
		try {
			log("CLIENT: Connecting to " + serverName + " on port " + port);
			Socket socket = new Socket(serverName, port);
			
			log("CLIENT: Just connected to " + socket.getRemoteSocketAddress());

			in  = new InputThread(new BufferedReader(new InputStreamReader(socket.getInputStream())));
			out = new OutputThread(new PrintWriter(socket.getOutputStream(), true));
			
			long startTime = System.currentTimeMillis();
			while(!in.hasMessage()) {
				if (System.currentTimeMillis() - startTime > connectTimeoutMillis) {
					log("CLIENT: Server response timed out... Disconnecting");
					out.sendMessage(FLAG_CLIENT_DISCONNECT);
					Thread.sleep(500);
					throw new TimeLimitExceededException();
				}
			}
			
			// Expecting Server.FLAG_SERVER_ACCEPT
			loadCommand();
			log("CLIENT: Message flag from server: " + loadedCommand.peek());
			if (!loadedCommand.poll().equals(Server.FLAG_SERVER_ACCEPT)) {
			    log("CLIENT: Server rejected the client");
			    throw new Server.ServerRejectionException("Server rejected the client");
			}
			
			log("CLIENT: Responding with accept message and sending client info...");
			out.sendMessage(Parser.createRawCommand(Client.FLAG_CLIENT_CONNECT));
			out.sendMessage(Parser.createRawCommand(FLAG_CLIENT_INFO, username));
			
			// Expecting Flag.PLAYER_ID
			log("CLIENT: Waiting for server's response...");
			waitAndLoadNextMessage();
			log("CLIENT: Message flag from server: " + loadedCommand.peek());
            if (!loadedCommand.poll().equals(Server.FLAG_NEW_CLIENT)) {
                log("Server sent an unexpected message");
                throw new Exception("Server sent unexpected message");
            }
            
			id = Integer.parseInt(loadedCommand.poll());
			log("CLIENT: Server responsed: client ID=" + id);
			
			if (id == 0) {
				firstToConnect = true;
			}

			synchronized(connectStatus) {
				connectStatus = CONNECT_PASSED;
			}

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
		    e.printStackTrace();
			synchronized(connectStatus) {
				connectStatus = CONNECT_FAILED;
			}
			killClient();
		}
	}
	
	protected void waitAndLoadNextMessage() throws Exception {
	    in.waitForMessage(0);
	    loadCommand();
	}
	
	protected void log(String s) {
	    logStream.println(s);
	}
}
