
import akka.actor.{ ActorSystem, Props }
import com.frenchcoder.wits.{ ServiceProxy, ServiceRegistry }
import com.typesafe.config.ConfigFactory
import scala.io.StdIn

object SampleClientMain {

  def main(args: Array[String]): Unit = {

    val port = if(args.isEmpty) "2551" else args(0)

    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load())

    implicit val system = ActorSystem("ClusterSystem", config)

    println(s"\u001B[32mStarting SampleClient, Ctrl+D to stop and go back to the console...\u001B[0m")

    // Start registry
    ServiceRegistry.create(system)

    // Start service proxy
    val proxy = system.actorOf(Props(new ServiceProxy(SampleServiceTag())))

    // Start Client
    system.actorOf(Props(new SampleClient(proxy)))

    while(StdIn.readLine() != null) {}
    system.terminate()
  }
}
