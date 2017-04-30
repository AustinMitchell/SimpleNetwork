package simple.network;

import java.io.BufferedReader;
import java.util.*;

public class InputThread implements Runnable {
	private BufferedReader in;
	private Queue<String> updateIn;
	private boolean exit, dead;
	
	public InputThread(BufferedReader in) {
		this.in = in;
		updateIn = new LinkedList<String>();
		exit = false;
		dead = false;
		
		new Thread(this).start();
	}
	
	public boolean hasMessage() throws Exception {
		if (dead) {
			throw new Exception();
		}
		
		synchronized(updateIn) {
			return !updateIn.isEmpty();
		}
	}
	public String readMessage() throws Exception {
		if (dead) {
			throw new Exception();
		}
		synchronized(updateIn) {
			return updateIn.poll();
		}
	}
	
	public void killThread() {
		exit = true;
	}
	
	public boolean hasThreadDied() {
		return dead;
	}
	
	@Override
	public void run() {
		try {
			while (!exit) {
				String message = in.readLine();
				synchronized (updateIn) {
					updateIn.add(message);
				}
			}
		} catch (Exception e) {
			dead = true;
		}
	}
}
