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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NavigatorAgent extends Agent {

    String nickname = "NavigatorAgent";
    AID id = new AID(nickname, AID.ISLOCALNAME);
    private Hashtable<AID, WumpusCoordinates> agents_coords;
    private Hashtable<AID, LinkedList<int[]>> agentsWayStory;

    private boolean moveRoom = false;
    private int agentX;
    private int agentY;

    WumpusEnvironment world;
    public static java.util.HashMap<Integer, String> sentenceActions = new java.util.HashMap<Integer, String>() {{
        put(1, "Move forward now");
        put(2, "Turn right here");
        put(3, "Turn left here");
        put(4, "Just shoot here");
        put(5, "Just grab gold");
        put(6, "Just climb here");
    }};

    public static final String START = "start";
    public static final String WUMPUS = "wumpus";
    public static final String HOLE = "hole";
    public static final String BREEZE = "breeze";
    public static final String STENCH = "stench";
    public static final String SCREAM = "scream";
    public static final String GOLD = "gold";
    public static final String BUMP = "bump";
    public static int STATE_NOTHING = -1;
    public static int STATE_TRUE = 1;
    public static int STATE_FALSE = 2;
    public static int STATE_POSSIBLE = 3;

    public static java.util.HashMap<String, String> perceptionCodes = new java.util.HashMap<String, String>() {{
        put(BREEZE, "breeze");
        put(WUMPUS, "wumpus");
        put(HOLE, "hole");
        put(STENCH, "stench");
        put(SCREAM, "scream");
        put(GOLD, "gold");
    }};
    @Override
    protected void setup() {
        System.out.println("Navigator agent " + getAID().getName() + " started");
        world = new WumpusEnvironment();
        agentsWayStory = new Hashtable<>();
        agents_coords = new Hashtable<>();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("navigator-agent");
        sd.setName("navigator-agent");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new SpeleologistRequestsServer());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Navigator-agent " + getAID().getName() + " terminating.");
    }

    private class SpeleologistRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                AID request_agent = msg.getSender();
                if (agentsWayStory.get(request_agent) == null) {
                    LinkedList<int[]> agentWay = new LinkedList<>();
                    agentsWayStory.put(request_agent, agentWay);
                }
                WumpusCoordinates request_agent_position = agents_coords.get(request_agent);
                if (request_agent_position == null) {
                    request_agent_position = new WumpusCoordinates();
                    agents_coords.put(request_agent, request_agent_position);
                }
                String location = parseSpeleologMessage(msg.getContent());
                location = location.substring(1, location.length() - 1);
                String[] room_info = location.split(", ");
                System.out.println("Room states: " + Arrays.toString(room_info));
                String[] actions = get_actions(request_agent, request_agent_position, room_info);
                ACLMessage reply = msg.createReply();

                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(sentenceActions.get(actions));
                myAgent.send(reply);
            } else {
                block();
            }
        }
        private String parseSpeleologMessage(String in) {
            for (Map.Entry<String, String> entry : perceptionCodes.entrySet()) {
                String value = entry.getValue();
                Pattern pattern = Pattern.compile("\\b" + value + "\\b", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(in);
                if (matcher.find()) {
                    String res = matcher.group();
                    return  res.length() > 0 ? res : "";

                }
            }
            return in;
        }
    }

    private String[] get_actions(AID request_agent, WumpusCoordinates request_agent_position, String[] room_info) {

        int[] actions;
        WumpusRoom checking_room = world.getGrid().get(request_agent_position);
        if (checking_room == null) {
            checking_room = new WumpusRoom();
            world.getGrid().put(request_agent_position, checking_room);
        }

        if (!Arrays.asList(room_info).contains(BUMP)) {
            LinkedList<int[]> agentStory = agentsWayStory.get(request_agent);
            agentStory.add(new int[]{request_agent_position.getX(), request_agent_position.getY()});
            request_agent_position.setX(agentX);
            request_agent_position.setY(agentY);
            if (world.getGrid().get(request_agent_position).getExist() != NavigatorAgent.STATE_TRUE) {
                world.getGrid().get(request_agent_position).setExist(NavigatorAgent.STATE_TRUE);
                System.out.println("Speleologist is alive");
            }
            moveRoom = false;
        } else {
            WumpusCoordinates helpWumpusCoordinates = new WumpusCoordinates(agentX, agentY);
            world.getGrid().get(helpWumpusCoordinates).setExist(NavigatorAgent.STATE_FALSE);
        }
        checking_room = world.getGrid().get(request_agent_position);
        if (checking_room == null) {
            checking_room = new WumpusRoom();
            world.getGrid().put(request_agent_position, checking_room);
        }

        if (checking_room.getOk() != NavigatorAgent.STATE_TRUE) {
            checking_room.setOk(NavigatorAgent.STATE_TRUE);
        }
        for (String event : room_info) {
            checking_room.addEvent(event);
        }
        updateNearestStates(request_agent_position);
        if (world.isWumpusAlive() && world.getWumpusRoomCount() > 2) {
            WumpusCoordinates wumpusPosition = world.getWumpusPosition();
            actions = getNextAction(request_agent_position, wumpusPosition, SpeleologistAgent.SHOOT_ARROW);
        } else {
            WumpusCoordinates[] nextOkRooms = getOkRooms(request_agent, request_agent_position);
            int best_candidate = -1;
            int candidate_status = -1;
            for (int i = 0; i < nextOkRooms.length; ++i) {
                WumpusCoordinates candidate_room = nextOkRooms[i];

                if (candidate_room.getX() > request_agent_position.getX()) {
                    best_candidate = i;
                    System.out.println("1");
                    break;
                } else if (candidate_room.getY() > request_agent_position.getY()) {
                    if (candidate_status < 3) {
                        System.out.println("2");
                        candidate_status = 3;
                    } else continue;
                } else if (candidate_room.getX() < request_agent_position.getX()) { // влево
                    if (candidate_status < 2) {
                        System.out.println("3");
                        candidate_status = 2;
                    } else continue;
                } else {
                    if (candidate_status < 1) {
                        System.out.println("4");
                        candidate_status = 1;
                    } else continue;
                }
                best_candidate = i;
            }

            actions = getNextAction(request_agent_position, nextOkRooms[best_candidate], SpeleologistAgent.MOVE);
            System.out.println("Navigator advice: " + Arrays.toString(actions));
        }

        String[] language_actions = new String[actions.length];
        for (int i = 0; i < actions.length; ++i) {
            language_actions[i] = SpeleologistAgent.actionCodes.get(actions[i]);
        }
        return language_actions;
    }

    private int[] getNextAction(WumpusCoordinates request_agent_position, WumpusCoordinates nextOkRoom, int action) {
        agentX = request_agent_position.getX();
        agentY = request_agent_position.getY();
        int look;
        if (request_agent_position.getY() < nextOkRoom.getY()) {
            agentY += 1;
            look = SpeleologistAgent.LOOK_UP;
        } else if (request_agent_position.getY() > nextOkRoom.getY()) {
            agentY -= 1;
            look = SpeleologistAgent.LOOK_DOWN;
        } else if (request_agent_position.getX() < nextOkRoom.getX()) {
            agentX += 1;
            look = SpeleologistAgent.LOOK_RIGHT;
        } else {
            agentX -= 1;
            look = SpeleologistAgent.LOOK_LEFT;
        }
        moveRoom = true;

        return new int[]{look, action};
    }

    private WumpusCoordinates[] getOkRooms(AID request_agent, WumpusCoordinates request_agent_position) {
        WumpusCoordinates[] okNeighbors = getNeighborsPosition(request_agent_position);
        ArrayList<WumpusCoordinates> okWumpusCoordinates = new ArrayList<>();
        for (WumpusCoordinates wumpusCoordinates : okNeighbors) {
            this.world.getGrid().putIfAbsent(wumpusCoordinates, new WumpusRoom());
            if ((this.world.getGrid().get(wumpusCoordinates).getOk() == NavigatorAgent.STATE_TRUE
                    && this.world.getGrid().get(wumpusCoordinates).getNoWay() != NavigatorAgent.STATE_TRUE
                    && this.world.getGrid().get(wumpusCoordinates).getExist() != NavigatorAgent.STATE_FALSE
            ) ||
                    this.world.getGrid().get(wumpusCoordinates).getOk() == NavigatorAgent.STATE_NOTHING) {
                okWumpusCoordinates.add(wumpusCoordinates);
            }
        }
        if (okWumpusCoordinates.size() == 0) {
            int x = agentsWayStory.get(request_agent).getLast()[0];
            int y = agentsWayStory.get(request_agent).getLast()[1];
            okWumpusCoordinates.add(new WumpusCoordinates(x, y));
            this.world.getGrid().get(request_agent_position).setNoWay(STATE_TRUE);
        }
        return okWumpusCoordinates.toArray(new WumpusCoordinates[0]);
    }

    private WumpusRoom[] getNeighborsRoom(WumpusCoordinates request_agent_position) {
        WumpusCoordinates rightNeighbor = new WumpusCoordinates(request_agent_position.getX() + 1, request_agent_position.getY());
        WumpusCoordinates upNeighbor = new WumpusCoordinates(request_agent_position.getX(), request_agent_position.getY() + 1);
        WumpusCoordinates leftNeighbor = new WumpusCoordinates(request_agent_position.getX() - 1, request_agent_position.getY());
        WumpusCoordinates bottomNeighbor = new WumpusCoordinates(request_agent_position.getX(), request_agent_position.getY() - 1);
        WumpusRoom rightRoom = world.getGrid().get(rightNeighbor);
        if (rightRoom == null) {
            rightRoom = new WumpusRoom();
            world.getGrid().put(rightNeighbor, rightRoom);
        }
        WumpusRoom upRoom = world.getGrid().get(upNeighbor);
        if (upRoom == null) {
            upRoom = new WumpusRoom();
            world.getGrid().put(rightNeighbor, upRoom);
        }
        WumpusRoom leftRoom = world.getGrid().get(leftNeighbor);
        if (leftRoom == null) {
            leftRoom = new WumpusRoom();
            world.getGrid().put(rightNeighbor, leftRoom);
        }
        WumpusRoom bottomRoom = world.getGrid().get(bottomNeighbor);
        if (bottomRoom == null) {
            bottomRoom = new WumpusRoom();
            world.getGrid().put(rightNeighbor, bottomRoom);
        }
        WumpusRoom[] rooms = new WumpusRoom[]{rightRoom, upRoom, leftRoom, bottomRoom};
        return rooms;
    }

    private WumpusCoordinates[] getNeighborsPosition(WumpusCoordinates request_agent_position) {
        WumpusCoordinates rightNeighbor = new WumpusCoordinates(request_agent_position.getX() + 1, request_agent_position.getY());
        WumpusCoordinates upNeighbor = new WumpusCoordinates(request_agent_position.getX(), request_agent_position.getY() + 1);
        WumpusCoordinates leftNeighbor = new WumpusCoordinates(request_agent_position.getX() - 1, request_agent_position.getY());
        WumpusCoordinates bottomNeighbor = new WumpusCoordinates(request_agent_position.getX(), request_agent_position.getY() - 1);
        ;
        return new WumpusCoordinates[]{rightNeighbor, upNeighbor, leftNeighbor, bottomNeighbor};
    }

    private void updateNearestStates(WumpusCoordinates request_agent_position) {
        WumpusRoom currentRoom = world.getGrid().get(request_agent_position);
        WumpusRoom[] roomList = getNeighborsRoom(request_agent_position);

        if (currentRoom.getStench() == NavigatorAgent.STATE_TRUE) {
            world.setWumpusRoomCount(world.getWumpusRoomCount() + 1);
            for (WumpusRoom room : roomList) {
                if (room.getWumpus() == NavigatorAgent.STATE_NOTHING) {
                    room.setOk(NavigatorAgent.STATE_POSSIBLE);
                    room.setWumpus(NavigatorAgent.STATE_POSSIBLE);
                }
            }
        }
        if (currentRoom.getBreeze() == NavigatorAgent.STATE_TRUE) {
            for (WumpusRoom room : roomList) {
                if (room.getHole() == NavigatorAgent.STATE_NOTHING) {
                    room.setOk(NavigatorAgent.STATE_POSSIBLE);
                    room.setHole(NavigatorAgent.STATE_POSSIBLE);
                }
            }
        }
        if (currentRoom.getBreeze() == NavigatorAgent.STATE_FALSE && currentRoom.getStench() == NavigatorAgent.STATE_FALSE) {
            for (WumpusRoom room : roomList) {
                room.setOk(NavigatorAgent.STATE_TRUE);
                room.setWumpus(NavigatorAgent.STATE_FALSE);
                room.setHole(NavigatorAgent.STATE_FALSE);
            }
        }
    }

}

class WumpusEnvironment {

    private Hashtable<WumpusCoordinates, WumpusRoom> Grid;
    private WumpusCoordinates wumpusPosition;
    
    private boolean isWumpusAlive;
    private int wumpusRoomCount;

    WumpusEnvironment() {
        Grid = new Hashtable<>();
        isWumpusAlive = true;
        wumpusRoomCount = 0;
    }

    public WumpusCoordinates getWumpusPosition() {
        int xWumpusCoord = 0;
        int yWumpusCoord = 0;

        Set<WumpusCoordinates> keys = Grid.keySet();
        for (WumpusCoordinates roomPosition : keys) {
            WumpusRoom room = Grid.get(roomPosition);
            if (room.getWumpus() == NavigatorAgent.STATE_POSSIBLE) {
                xWumpusCoord += roomPosition.getX();
                yWumpusCoord += roomPosition.getY();
            }
        }
        xWumpusCoord /= wumpusRoomCount;
        yWumpusCoord /= wumpusRoomCount;
        this.wumpusPosition = new WumpusCoordinates(xWumpusCoord, yWumpusCoord);
        return this.wumpusPosition;
    }

    public Hashtable<WumpusCoordinates, WumpusRoom> getGrid() {
        return Grid;
    }


    public boolean isWumpusAlive() {
        return isWumpusAlive;
    }

    public void setWumpusAlive(boolean wumpusAlive) {
        isWumpusAlive = wumpusAlive;
    }

    public int getWumpusRoomCount() {
        return wumpusRoomCount;
    }

    public void setWumpusRoomCount(int wumpusRoomCount) {
        this.wumpusRoomCount = wumpusRoomCount;
    }
}

class WumpusRoom {
    private int exist;
    private int stench;
    private int breeze;
    private int hole;
    private int wumpus;
    private int ok;
    private int gold;
    private int noWay;
    private int scream;
    private int bump;

    public WumpusRoom() {
        this.exist = NavigatorAgent.STATE_NOTHING;
        this.stench = NavigatorAgent.STATE_NOTHING;
        this.breeze = NavigatorAgent.STATE_NOTHING;
        this.hole = NavigatorAgent.STATE_NOTHING;
        this.wumpus = NavigatorAgent.STATE_NOTHING;
        this.ok = NavigatorAgent.STATE_NOTHING;
        this.gold = NavigatorAgent.STATE_NOTHING;
        this.noWay = NavigatorAgent.STATE_NOTHING;
        this.scream = NavigatorAgent.STATE_NOTHING;
        this.bump = NavigatorAgent.STATE_NOTHING;
    }

    public void addEvent(String event_name) {
        switch (event_name) {
            case NavigatorAgent.START:
                break;
            case NavigatorAgent.WUMPUS:
                this.setWumpus(NavigatorAgent.STATE_TRUE);
                break;
            case NavigatorAgent.HOLE:
                this.setHole(NavigatorAgent.STATE_TRUE);
                break;
            case NavigatorAgent.BREEZE:
                this.setBreeze(NavigatorAgent.STATE_TRUE);
                break;
            case NavigatorAgent.STENCH:
                this.setStench(NavigatorAgent.STATE_TRUE);
                break;
            case NavigatorAgent.SCREAM:
                this.setScream(NavigatorAgent.STATE_TRUE);
                break;
            case NavigatorAgent.GOLD:
                this.setGold(NavigatorAgent.STATE_TRUE);
                break;
            case NavigatorAgent.BUMP:
                this.setBump(NavigatorAgent.STATE_TRUE);
                break;
        }
    }

    public int getExist() {

        return exist;
    }

    public void setExist(int exist) {

        this.exist = exist;
    }

    public int getStench() {

        return stench;
    }

    public void setStench(int stench) {

        this.stench = stench;
    }

    public int getBreeze() {

        return breeze;
    }

    public void setBreeze(int breeze) {

        this.breeze = breeze;
    }

    public int getHole() {

        return hole;
    }

    public void setHole(int hole) {

        this.hole = hole;
    }

    public int getWumpus() {

        return wumpus;
    }

    public void setWumpus(int wumpus) {

        this.wumpus = wumpus;
    }

    public int getOk() {

        return ok;
    }

    public void setOk(int ok) {

        this.ok = ok;
    }


    public void setGold(int gold) {

        this.gold = gold;
    }

    public int getNoWay() {

        return noWay;
    }


    public void setScream(int scream) {
        this.scream = scream;
    }


    public void setBump(int bump) {
        this.bump = bump;
        System.out.println("Game over");
    }

    public void setNoWay(int stateTrue) {
        this.noWay = stateTrue;
    }
}

class WumpusCoordinates {
    private int x;
    private int y;

    WumpusCoordinates() {
        this.x = 0;
        this.y = 0;
    }

    WumpusCoordinates(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        WumpusCoordinates position = (WumpusCoordinates) obj;
        return this.x == position.getX() && this.y == position.getY();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        return result;
    }

    public int getX() {

        return x;
    }

    public void setX(int x) {

        this.x = x;
    }

    public int getY() {

        return y;
    }

    public void setY(int y) {

        this.y = y;
    }
}