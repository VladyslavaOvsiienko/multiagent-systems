import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props

class HelloActor(myName: String) extends Actor {
  def receive = {
    case "hello" => println("hello from %s".format(myName))
    case _       => println("'huh?', said %s".format(myName))
  }
}

object Main extends App {
  val system = ActorSystem("HelloSystem")
  val helloActor = system.actorOf(Props(new HelloActor("Vlada")), name = "helloactor")

  helloActor ! "hello"
  helloActor ! "buenos dias"
}
