import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpeleologistAgent extends Agent {

    public static int LOOK_RIGHT = 0;
    public static int LOOK_LEFT = 1;
    public static int LOOK_UP = 2;
    public static int LOOK_DOWN = 3;
    public static int MOVE = 4;
    public static int SHOOT_ARROW = 5;
    public static int GOLD_IS_FOUND = 6;
    public static java.util.HashMap<Integer, String> perceptions = new java.util.HashMap<Integer, String>() {{
        put(1, "It's wumpus here");
        put(2, "It's hole here");
        put(3, "It's breeze here");
        put(4, "It's stench here");
        put(5, "It's scream here");
        put(6, "It's gold here");
    }};
    public static java.util.HashMap<Integer, String> actionCodes = new java.util.HashMap<Integer, String>() {{
        put(LOOK_RIGHT, "right");
        put(LOOK_LEFT, "left");
        put(LOOK_UP, "up");
        put(LOOK_DOWN, "down");
        put(MOVE, "move");
        put(SHOOT_ARROW, "shoot");
        put(GOLD_IS_FOUND, "take");
        put(CLIMB, "climb");
    }};


    public static String GO_INSIDE = "go inside";

    public static String WORLD_ID = "conversation with world";
    public static String NAVIGATOR_ID = "conversation with navigator navigator";

    private AID wumpusWorld;
    private AID navigatorAgent;
    private String currentWorldState = "";

    @Override
    protected void setup() {
        System.out.println("Speleologist agent " + getAID().getName() + " started");
        addBehaviour(new EnvironmentConnector());
    }
    @Override
    protected void takeDown() {
        System.out.println("Speleologist-agent " + getAID().getName() + " terminating.");
    }


    private class EnvironmentConnector extends Behaviour {
        private int step = 0;
        @Override
        public void action() {
            if (step == 0){
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("wumpus-world");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    if (result != null && result.length > 0) {
                        wumpusWorld = result[0].getName();
                        myAgent.addBehaviour(new EnvironmentPerformer());
                        ++step;
                    } else {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (FIPAException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public boolean done() {
            return step == 1;
        }
    }
    private class EnvironmentPerformer extends Behaviour {
        private MessageTemplate mt;

        private int step = 0;
        @Override
        public void action() {
            switch (step) {
                case 0:
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.addReceiver(wumpusWorld);
                    cfp.setContent(GO_INSIDE);
                    cfp.setConversationId(WORLD_ID);
                    cfp.setReplyWith("cfp"+System.currentTimeMillis());
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(WORLD_ID),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.CONFIRM) {
                            String answer = reply.getContent();
                            currentWorldState = answer;
                            myAgent.addBehaviour(new NavigatorPerformer());
                            step = 2;
                        }
                    }
                    else {
                        block();
                    }
                    break;
            }
        }
        @Override
        public boolean done() {
            return step == 2;
        }
    }

    private class NavigatorPerformer extends Behaviour {
        private int step = 0;
        private MessageTemplate mt;


        private String parseNavigatorMessage(String in) {
            for (Map.Entry<Integer, String> entry : actionCodes.entrySet()) {
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

        @Override
        public void action() {
            switch (step) {
                case 0: {
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("navigator-agent");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if (result != null && result.length > 0) {
                            navigatorAgent = result[0].getName();
                            ++step;
                        } else {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }
                }
                case 1: {
                    ACLMessage order = new ACLMessage(ACLMessage.INFORM);
                    order.addReceiver(navigatorAgent);
                    order.setContent(perceptions.get(currentWorldState));
                    order.setConversationId(NAVIGATOR_ID);
                    order.setReplyWith("order"+System.currentTimeMillis());
                    myAgent.send(order);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_ID),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 2;
                }
                case 2: {
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            String actions = reply.getContent();
                            actions = actions.substring(1, actions.length()-1);
                            String[] instructions = actions.split(", ");
                            if (instructions.length == 1){
                                sendGoldMessage();
                            }
                            else if (instructions.length == 2 && Objects.equals(instructions[1], actionCodes.get(SHOOT_ARROW))){
                                sendShootMessage(instructions[0]);
                            }
                            else if (instructions.length == 2 && Objects.equals(instructions[1], actionCodes.get(MOVE))){
                                sendActionMessage(instructions[0]);
                            }
                            else {
                                System.out.println("error");
                            }
                            ++step;
                        }
                    }
                    else {
                        block();
                    }
                    break;

                }
                case 3:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {

                        currentWorldState = parseNavigatorMessage(reply.getContent());
                        step = 1;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }
        @Override
        public boolean done() {
            return step == 4;
        }

        private void sendShootMessage(String instruction) {
            ACLMessage order = new ACLMessage(SHOOT_ARROW);
            order.addReceiver(wumpusWorld);
            order.setContent(instruction);
            order.setConversationId(NAVIGATOR_ID);
            order.setReplyWith("order"+System.currentTimeMillis());
            myAgent.send(order);
            mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_ID),
                    MessageTemplate.MatchInReplyTo(order.getReplyWith()));
        }

        private void sendGoldMessage() {
            ACLMessage order = new ACLMessage(GOLD_IS_FOUND);
            order.addReceiver(wumpusWorld);
            order.setContent("Take");
            order.setConversationId(NAVIGATOR_ID);
            order.setReplyWith("order"+System.currentTimeMillis());
            myAgent.send(order);
            mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_ID),
                    MessageTemplate.MatchInReplyTo(order.getReplyWith()));
        }
        private void sendActionMessage(String instruction) {
            ACLMessage order = new ACLMessage(MOVE);
            order.addReceiver(wumpusWorld);
            order.setContent(instruction);
            order.setConversationId(NAVIGATOR_ID);
            order.setReplyWith("order"+System.currentTimeMillis());
            myAgent.send(order);
            mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_ID),
                    MessageTemplate.MatchInReplyTo(order.getReplyWith()));
        }
    }
}