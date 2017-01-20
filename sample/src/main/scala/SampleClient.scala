
import akka.actor.{ Actor, ActorRef }
import com.frenchcoder.wits.ServiceUnavailable
import scala.concurrent.duration._

class SampleClient(service: ActorRef) extends Actor {

  case object Tick
  val system = context.system
  implicit val ec = system.dispatcher

  system.scheduler.schedule(1 second, 1 second, self, Tick)

  def receive = {
    case Tick =>
      println("Send message to remote service")
      service ! ToUpperRequest("Please make me uppercase")
    case ToUpperResponse(text) => println(s"Got response : '$text'")
    case ServiceUnavailable(_,_) => println("Service is unavailable")

  }

}

