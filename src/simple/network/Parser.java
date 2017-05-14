package simple.network;

import java.util.*;
import java.util.regex.Pattern;

public class Parser {
    private static class RandString {
        private static final char[] symbols = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z'};
        private static       char[] buffer  = new char[10];
        private static final Random random  = new Random();

        public static void setLength(int length) {
            buffer = new char[length]; 
        }
        
        public static String nextString() {            
            for (int i = 0; i < buffer.length; i++) 
                buffer[i] = symbols[random.nextInt(symbols.length)];
            return new String(buffer);
        }
    }

    
    public static interface ParseRule {
        public String handleParse(Queue<String> command);
    }
    
    // Creates command strings where each argument is separated by a random alphanumeric string, which is prepended on the entire command.
    public static String createRawCommand(String...args) {
        String ARG_DELIMIT = "{" + RandString.nextString() + "}";
        String newCommand  = ARG_DELIMIT;
        for (int i=0; i<args.length; i++) {
            newCommand += args[i];
            if (i != args.length-1) {
                newCommand += ARG_DELIMIT;
            }
        }
        return newCommand;
    }

    // Takes a raw command built with createRawCommand and parses its arguments. First twelve characters should be an alphanumeric string surrounded by a pair of curly braces
    public static void acceptRawCommands(Queue<Queue<String>> commandQueue, String newRawCommand) {
        String ARG_DELIMIT = newRawCommand.substring(0, 12);
        Queue<String> newCommand = new LinkedList<String>();
        for (String arg: newRawCommand.substring(12).split(Pattern.quote(ARG_DELIMIT))) {
            newCommand.add(arg);
        }
        commandQueue.add(newCommand);
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
    
    public void clearParseRules() {
        parseRules = new HashMap<String, ParseRule>();
    }
    
	public String parse(Queue<String> command) {
	    String ruleKey = command.peek();
	    if (!parseRules.containsKey(ruleKey)) {
            throw new RuntimeException("Attempted to use a ruleKey that doesn't exist: " + ruleKey);
        }
			
		return parseRules.get(ruleKey).handleParse(command);
	}

}
