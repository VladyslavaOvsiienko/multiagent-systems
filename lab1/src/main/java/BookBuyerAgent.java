import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;

public class BookBuyerAgent extends Agent{
    // title of the book to buy
    private String targetBookTitle;
    // list of known seller agents
    private AID[] sellerAgents = {new AID("seller1", AID.ISLOCALNAME),
            new AID("seller2", AID.ISLOCALNAME)};

    // Put agent initializations
     protected void setup() {
    // Printout a welcome message
         System.out.println("Hello! Buyer-agent "+getAID().getName()+" is ready.");
    // Get title of  book to buy
         Object[] args = getArguments();
        if (args != null && args.length > 0) {
          targetBookTitle = (String) args[0];
          System.out.println("Trying to buy "+targetBookTitle);
            // Add a TickerBehaviour for requesting to seller agents every minute
            addBehaviour(new TickerBehaviour(this, 60000) {
                protected void onTick() {
                    myAgent.addBehaviour(new RequestPerformer());
                }
            } );
        }
          else {
         // terminate
            System.out.println("No book title specified");
            doDelete();
          }
     }
     // Put agent clean-up operations here
    protected void takeDown() {
     System.out.println("Buyer-agent "+getAID().getName()+" terminating.");
     }
}

