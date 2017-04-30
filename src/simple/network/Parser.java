package simple.network;

import java.util.*;

public class Parser {
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
     
    
	/*
	 * add an incoming flag to guiFlags queue this method is capable of parsing
	 * multiple commands passed as a single string then add each command to the
	 * queue as a single string
	 * 
	 * NOTE: commands must be separated by the single line character "\n" DO NOT
	 * insert spaces before or after that new line character
	 */
	public static void guiSplitter(Queue<String> guiFlags, String flag) {

		if (flag.contains(Flag.NEW_COM)) // check if there are multiple commands
		{
			String commands[] = flag.split(Flag.NEW_COM);
			for (int i = 0; i < commands.length; i++) {
				guiFlags.add(commands[i]);
			}
		} else { // the case of one single command
			guiFlags.add(flag);
		}
	}

	public static String networkSplitter(String flag, GameState game) {
		String[] commands;
		if (flag.contains(Flag.NEW_COM)) // check if there are multiple commands
		{
			commands = flag.split(Flag.NEW_COM);
			for (int i = 0; i < commands.length; i++) {
				commands[i] = parse(commands[i].split(":"), game);
			}
			return String.join(Flag.NEW_COM, commands);
		} else
			// the case of one single command
			return parse(flag.split(":"), game);
	}

	public static String parse(String[] command, GameState game) {
		String result = "";
		synchronized (game) {
			switch (command[0]) {

			// First set of commands are names of players. Next, CARDS_BEGIN,
			// then a series of pairs for type and value for
			// each card in the new deck
			case Flag.INIT_CLIENT: { // initializes draw deck on the client side
				int i = 1;
				ArrayList<Player> players = new ArrayList<Player>();
				while (!command[i].equals(Flag.CARDS_BEGIN)) {
					players.add(new Player(command[i]));
					i++;
				}
				i++;
				int[][] cardData = new int[(command.length - i) / 2][2];
				for (int j = 0; j < cardData.length; j++) {
					cardData[j][0] = Integer.parseInt(command[j * 2 + i]);
					cardData[j][1] = Integer.parseInt(command[j * 2 + i + 1]);
				}
				game.initializeClient(players, cardData);
				result = Flag.INIT_CLIENT;
				break;
			}

			// Each pair of commands is the type and value of each card in the
			// newly shuffled deck
			case Flag.RENEW_DECK: {

				int[][] cardData = new int[(command.length - 1) / 2][2];
				for (int j = 0; j < cardData.length; j++) {
					cardData[j][0] = Integer.parseInt(command[j * 2 + 1]);
					cardData[j][1] = Integer.parseInt(command[j * 2 + 2]);
				}

				game.renewDrawDeck(cardData);
				result = Flag.RENEW_DECK;

				break;
			}

			case Flag.GET_TOKEN: {
				if (Integer.parseInt(command[2]) == Type.PURPLE) {
					game.setTurn(Integer.parseInt(command[1]));
				}
				break;
			}

			case Flag.DRAW_CARD: {
				result = RulesEngine.drawCard(game);
				if (!game.hasTournamentStarted()) {
					result += Flag.NEW_COM + Flag.START_TOURNAMENT;
					if (Validator.canStartTournament(game)) {
						result += Flag.NEW_COM + Flag.CAN_START_TOURNAMENT
								+ ":true";
					} else {
						result += Flag.NEW_COM + Flag.CAN_START_TOURNAMENT
								+ ":false";
					}
				}
				break;
			}

			// First command is true or false depending on whether they withdrew
			case Flag.END_TURN: {
				result = RulesEngine.endTurn(game);
				
				if(RulesEngine.isTournamentOver(game)) {
					result += Flag.NEW_COM + Flag.PICK_TOKEN;
				}
				break;
			}
			
			case Flag.AWARD_TOKEN: {
				result = RulesEngine.awardToken(game, command[1]);

				String endGameResult = RulesEngine.endGame(game);
				if (!endGameResult.equals("")) {
					result += Flag.NEW_COM + endGameResult;

				}
				break;
			}

			case Flag.START_TOURNAMENT: {
				result = RulesEngine.startTournament(game);
				break;
			}

			// First command is the value of the new colour
			case Flag.SET_COLOUR: {
				result = RulesEngine.setColour(game, command[1]);
				break;
			}

			// Signifies that an action card will perform its action; No one
			// played IVANHOE on it.
			// First command is the player ID of who played the card, then
			// position of the card being played.
			// Possible other commands depending on the action taken.
			case Flag.ACTION_CARD: {
				int currPlayer = game.getTurn();
				int type = game.getAllPlayers().get(currPlayer).getHand()
						.getCard(Integer.parseInt(command[1])).getCardType();
				int value = game.getAllPlayers().get(currPlayer).getHand()
						.getCard(Integer.parseInt(command[1])).getCardValue();

				switch (value) {
					case Card.UNHORSE: {
						result = RulesEngine.unhorse(game,
								Integer.parseInt(command[1]),
								Integer.parseInt(command[2]));
						break;
					}
					case Card.CHANGE_WEAPON: {
						result = RulesEngine.changeWeapon(game,
								Integer.parseInt(command[1]),
								Integer.parseInt(command[2]));
						break;
					}
					case Card.DROP_WEAPON: {
						result = RulesEngine.dropWeapon(game,
								Integer.parseInt(command[1]));
						break;
					}
					case Card.BREAK_LANCE: {
						result = RulesEngine.breakLance(game,
								Integer.parseInt(command[1]),
								Integer.parseInt(command[2]));
						break;
					}
					case Card.RIPOSTE: {
						result = RulesEngine.riposte(game,
								Integer.parseInt(command[1]),
								Integer.parseInt(command[2]));
						break;
					}
					case Card.DODGE: {
						result = RulesEngine.dodge(game, 
								Integer.parseInt(command[1]),
								Integer.parseInt(command[2]),
								Integer.parseInt(command[3]));
						break;
					}
					case Card.RETREAT: {
						result = RulesEngine.retreat(game,
								Integer.parseInt(command[1]),
								Integer.parseInt(command[2]));
						break;
					}
					case Card.KNOCKDOWN: {
						result = RulesEngine.knockdown(game, 
								Integer.parseInt(command[1]),
								Integer.parseInt(command[2]),
								Integer.parseInt(command[3]));
						break;
					}
					case Card.OUTMANEUVER: {
						result = RulesEngine.outmaneuver(game,
								Integer.parseInt(command[1]));
						break;
					}
					case Card.CHARGE: {
						result = RulesEngine.charge(game,
								Integer.parseInt(command[1]));
						break;
					}
					case Card.COUNTERCHARGE: {
						result = RulesEngine.countercharge(game,
								Integer.parseInt(command[1]));
						break;
					}
					case Card.DISGRACE: {
						result = RulesEngine.disgrace(game,
								Integer.parseInt(command[1]));
						break;
					}
					case Card.ADAPT: {
						result = RulesEngine.adapt(game,
								Integer.parseInt(command[1]));
						break;
					}
					case Card.OUTWIT: {
						result = RulesEngine.outwit(game, 
								Integer.parseInt(command[1]),
								command[2],
								Integer.parseInt(command[3]),
								Integer.parseInt(command[4]),
								command[5],
								Integer.parseInt(command[6]));
						break;
					}
					case Card.SHIELD: {
						result = RulesEngine.shield(game, 
								Integer.parseInt(command[1]));
						break;
					}
					case Card.STUNNED: {
						result = RulesEngine.stun(game, 
								Integer.parseInt(command[1]),
								Integer.parseInt(command[2]));
						break;
					}
					default: {
						result = RulesEngine.unimplementedActionCard(game,
								Integer.parseInt(command[1]));
						break;
					}
				}

				break;
			}

			case Flag.IVANHOE_PLAYED: {
				result = RulesEngine.ivanhoe(game,
						Integer.parseInt(command[1]),
						Integer.parseInt(command[2]));
				break;
			}

			// First command is the position of the card being played. Possible
			// other commands depending on the action taken.
			case Flag.CARD: {
				int currPlayer = game.getTurn();
				int type = game.getAllPlayers().get(currPlayer).getHand()
						.getCard(Integer.parseInt(command[1])).getCardType();
				int value = game.getAllPlayers().get(currPlayer).getHand()
						.getCard(Integer.parseInt(command[1])).getCardValue();

				switch (type) {
				case Type.ACTION:
					result = String.join(":", command);
					break;
				default:
					result = RulesEngine.playValueCard(game,
							Integer.parseInt(command[1]));
					break;
				}
				break;
			}
			}

		}
		return result;
	}

}
