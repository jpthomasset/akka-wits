import com.frenchcoder.wits.ServiceActor
import akka.actor.Actor.Receive

class SampleService extends ServiceActor(SampleServiceTag()) {

  def receive : Receive = {
    case ToUpperRequest(text) =>
      println(s"Got request to uppercase '$text'")
      sender() ! ToUpperResponse(text.toUpperCase())
  }
}
