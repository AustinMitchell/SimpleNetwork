package simple.network;

import java.util.*;

public class Parser {
    public static interface ParseRule {
        public String handleParse(Queue<String> command);
    }
    
    public static final String ARG_DELIMIT = "|";
    public static final String COM_DELIMIT = ";";
    
    public static String createRawCommand(String...args) {
        String newCommand = "";
        for (int i=0; i<args.length; i++) {
            newCommand += args[i];
            if (i != args.length-1) {
                newCommand += ARG_DELIMIT;
            }
        }
        return newCommand;
    }
    public static String createRawCommandList(String...commands) {
        String newCommandList = "";
        for (int i=0; i<commands.length; i++) {
            newCommandList += commands[i];
            if (i != commands.length-1) {
                newCommandList += COM_DELIMIT;
            }
        }
        return newCommandList;
    }
    
    public static void acceptRawCommands(Queue<Queue<String>> commandQueue, String newCommands) {
        for (String command: newCommands.split(COM_DELIMIT)) {
            Queue<String> newCommand = new LinkedList<String>();
            for (String arg: command.split(ARG_DELIMIT)) {
                newCommand.add(arg);
            }
            commandQueue.add(newCommand);
        }
    }

    private Map<String, ParseRule> parseRules;
    
    public Parser() {
        parseRules = new HashMap<String, ParseRule>();
    }
    
    public void addParseRule(String ruleKey, ParseRule handler) {
        if (parseRules.containsKey(ruleKey)) {
            throw new RuntimeException("Attempted to add a ruleKey that already exists: " + ruleKey);
        }
        parseRules.put(ruleKey, handler);
    }
    
	public String parse(Queue<String> command) {
	    String ruleKey = command.peek();
	    if (!parseRules.containsKey(ruleKey)) {
            throw new RuntimeException("Attempted to use a ruleKey that doesn't exist: " + ruleKey);
        }
			
		return parseRules.get(ruleKey).handleParse(command);
	}

}
