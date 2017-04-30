package simple.network;

import java.io.PrintWriter;
import java.util.*;

public class OutputThread implements Runnable {
		private PrintWriter out;
		private Queue<String> updateOut;
		private boolean exit;
		
		public OutputThread(PrintWriter out) {
			this.out = out;
			updateOut = new LinkedList<String>();
			exit = false;
			
			new Thread(this).start();
		}
		
		public void sendMessage(String msg) {
			synchronized(updateOut) {
				updateOut.add(msg);
			}
		}
		
		public void killThread() {
			exit = true;
		}
		
		@Override
		public void run() {
			while (!exit) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				synchronized (updateOut) {
					if (!updateOut.isEmpty()) {
						out.println(updateOut.poll());
					}
				}
			}
		}
	}