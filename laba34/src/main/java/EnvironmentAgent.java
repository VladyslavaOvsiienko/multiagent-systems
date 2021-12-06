import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class EnvironmentAgent extends Agent {


    public static HashMap<Integer, String> states = new HashMap<Integer, String>() {{
        put(START, NavigatorAgent.START);
        put(WUMPUS, NavigatorAgent.WUMPUS);
        put(HOLE, NavigatorAgent.HOLE);
        put(BREEZE, NavigatorAgent.BREEZE);
        put(STENCH, NavigatorAgent.STENCH);
        put(SCREAM, NavigatorAgent.SCREAM);
        put(GOLD, NavigatorAgent.GOLD);
        put(BUMP, NavigatorAgent.BUMP);
    }};

    private static final int START = -1;
    private static final int WUMPUS = 1;
    private static final int HOLE = 2;
    private static final int BREEZE = 3;
    private static final int STENCH = 4;
    private static final int SCREAM = 5;
    private static final int GOLD = 6;
    private static final int BUMP = 7;


    private Room[][] Cave;
    private HashMap<AID, Coordinates> speleologists;
    private int time;

    String nickname = "WumpusWorld";
    AID id = new AID(nickname, AID.ISLOCALNAME);

    @Override
    protected void setup() {
        System.out.println("Environment agent " + getAID().getName() + " started");
        speleologists = new HashMap<>();
        createCave();
        printCave();
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("wumpus-world");
        sd.setName("wumpus-world");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new SpeleologistRequestsServer());
        addBehaviour(new SpeleologistShootPerformer());
        addBehaviour(new FoundGoldPerformer());
        addBehaviour(new SpeleologistActionPerformer());
    }
    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Environment-agent " + getAID().getName() + " terminating.");
    }
    private void createCave() {
        this.Cave = new Room[4][4];
        this.Cave[0][0] = new Room();
        this.Cave[0][1] = new Room(BREEZE);
        this.Cave[0][2] = new Room(HOLE);
        this.Cave[0][3] = new Room(BREEZE);
        this.Cave[1][0] = new Room(STENCH);
        this.Cave[1][3] = new Room(BREEZE);
        this.Cave[2][0] = new Room(WUMPUS, STENCH);
        this.Cave[2][1] = new Room(STENCH, GOLD);
        this.Cave[2][2] = new Room();
        this.Cave[2][3] = new Room(BREEZE);
        this.Cave[3][0] = new Room(STENCH);
        this.Cave[3][2] = new Room(BREEZE);
        this.Cave[3][3] = new Room(HOLE);
        for (int i = 0; i < this.Cave.length; i++){
            for (int j = 0; j < this.Cave[i].length; j++){
                if (this.Cave[i][j] == null) {
                    this.Cave[i][j] = new Room();
                }
            }

        }

    }
    
    private void printCave(){
        System.out.println("CAVE:");
        for (int i = 0; i < this.Cave.length; i++){
            for (int j = 0; j < this.Cave[i].length; j++){
                System.out.println("x: " + i + ", y: " + j + "; States: " +  Cave[i][j].events);
            }

        }
    }
    
    private class SpeleologistRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String message = msg.getContent();
                if (Objects.equals(message, SpeleologistAgent.GO_INSIDE)){
                    AID current_Speleologist = msg.getSender();
                    Coordinates speleologist_coordinates = speleologists.get(current_Speleologist);
                    if (speleologist_coordinates == null){
                        speleologists.put(current_Speleologist, new Coordinates(0, 0));
                    }
                    else {
                        speleologists.put(current_Speleologist, new Coordinates(0, 0));
                    }
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.CONFIRM);
                    reply.setContent(Cave[0][0].events.toString());
                    myAgent.send(reply);
                }
            }
            else {
                block();
            }
        }
    }
    private class SpeleologistShootPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.SHOOT_ARROW);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(SpeleologistAgent.SHOOT_ARROW);

                String message = msg.getContent();
                AID current_Speleologist = msg.getSender();
                Coordinates speleologist_coordinates = speleologists.get(current_Speleologist);

                int row = speleologist_coordinates.row;
                int column = speleologist_coordinates.column;
                String answer = "";
                if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_DOWN))){
                    for (int i = 0; i < row; ++i){
                        if (Cave[i][column].events.contains(EnvironmentAgent.states.get(WUMPUS))){
                            answer = NavigatorAgent.SCREAM;
                            System.out.println("Wumpus was killed");
                        }
                    }
                }
                else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_UP))){
                    for (int i = row+1; i < 4; ++i){
                        if (Cave[i][column].events.contains(EnvironmentAgent.states.get(WUMPUS))){
                            answer = NavigatorAgent.SCREAM;
                            System.out.println("Wumpus was killed");
                        }
                    }
                }
                else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_LEFT))){
                    for (int i = 0; i < column; ++i){
                        if (Cave[row][i].events.contains(EnvironmentAgent.states.get(WUMPUS))){
                            answer = NavigatorAgent.SCREAM;
                            System.out.println("Wumpus was killed");
                        }
                    }
                }
                else if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_RIGHT))){
                    for (int i = column+1; i < 4; ++i){
                        if (Cave[row][i].events.contains(EnvironmentAgent.states.get(WUMPUS))){
                            answer = NavigatorAgent.SCREAM;
                            System.out.println("Wumpus was killed");

                        }
                    }
                }

                reply.setContent(answer);

                myAgent.send(reply);
                time++;
            }
            else {
                block();
            }
        }
    }
    private class SpeleologistActionPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.MOVE);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(SpeleologistAgent.MOVE);

                String message = msg.getContent();
                AID current_Speleologist = msg.getSender();
                Coordinates speleologist_coordinates = speleologists.get(current_Speleologist);
                System.out.println("Current agent coords: " + speleologist_coordinates.row + " " + speleologist_coordinates.column);
                if (speleologist_coordinates == null){
                    speleologists.put(current_Speleologist, new Coordinates(0, 0));
                    speleologist_coordinates = speleologists.get(current_Speleologist);
                }
                int row = speleologist_coordinates.row;
                int column = speleologist_coordinates.column;
                if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_DOWN))){
                    row -= 1;
                }
                else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_UP))){
                    row += 1;
                }
                else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_LEFT))){
                    column -=1;
                }
                else if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_RIGHT))){
                    column += 1;
                }
                if (row > -1 && column > -1 && row < 4 && column < 4){
                    speleologist_coordinates.column = column;
                    speleologist_coordinates.row = row;
                    reply.setContent(Cave[row][column].events.toString());
                }
                else {
                    reply.setContent(String.valueOf(new ArrayList<String>(){{
                        add(NavigatorAgent.BUMP);
                    }}));
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }
    private class FoundGoldPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.GOLD_IS_FOUND);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                AID current_Speleologist = msg.getSender();
                Coordinates speleologist_coordinates = speleologists.get(current_Speleologist);
                if (speleologist_coordinates == null){
                    speleologists.put(current_Speleologist, new Coordinates(0, 0));
                }
                else {
                    if (Cave[speleologist_coordinates.row][speleologist_coordinates.column].events.contains(EnvironmentAgent.states.get(GOLD))){
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(SpeleologistAgent.GOLD_IS_FOUND);
                        reply.setContent("GOLD");
                        myAgent.send(reply);
                    }
                }
            }
            else {
                block();
            }
        }
    }
}
class Room {
    ArrayList<String> events = new ArrayList<>();
    Room (int... args){
        for (int i: args){
            events.add(EnvironmentAgent.states.get(i));
        }
    }
}
class Coordinates {
    int row;
    int column;
    Coordinates(int row, int column){
        this.row = row;
        this.column = column;
    }
}