
import akka.actor.{ ActorSystem, Props }
import com.frenchcoder.wits.ServiceRegistry
import com.typesafe.config.ConfigFactory
import scala.io.StdIn

object SampleServiceMain {

  def main(args: Array[String]): Unit = {
    val port = if(args.isEmpty) "2552" else args(0)

    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load())

    implicit val system = ActorSystem("ClusterSystem", config)

    println(s"\u001B[32mStarting SampleService, Ctrl+D to stop and go back to the console...\u001B[0m")

    // Start registry
    ServiceRegistry.create(system)

    // Start Service
    system.actorOf(Props[SampleService])

    while(StdIn.readLine() != null) {}
    system.terminate()
  }
}
