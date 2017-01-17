package com.frenchcoder.wits

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.cluster.Cluster
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpecCallbacks, MultiNodeSpec }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import _root_.com.typesafe.config.ConfigFactory
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}


object ServiceRegistrySpecConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")

  def nodeList = Seq(node1, node2)

  nodeList foreach { role =>
    nodeConfig(role) {
      ConfigFactory.parseString(s"""
      # Disable legacy metrics in akka-cluster.
      akka.cluster.metrics.enabled=off
      # Enable metrics extension in akka-cluster-metrics.
      akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
      # Sigar native library extract location during tests.
      akka.cluster.metrics.native-library-extract-folder=target/native/${role.name}
      """)
    }
  }
 
  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = cluster
    akka.remote.log-remote-lifecycle-events = off
    akka.cluster.roles = [compute]
     // router lookup config ...
    """))
}

class ServiceRegistrySpecMultiJvmNode1 extends ServiceRegistrySpec
class ServiceRegistrySpecMultiJvmNode2 extends ServiceRegistrySpec

abstract class ServiceRegistrySpec extends MultiNodeSpec(ServiceRegistrySpecConfig) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  import ServiceRegistrySpecConfig._

  override def initialParticipants = roles.size
  override def beforeAll() = multiNodeSpecBeforeAll()
  override def afterAll() = multiNodeSpecAfterAll()
  

  def poison(actor: ActorRef) = {
    val probe = TestProbe()
    probe.watch(actor)
    system.stop(actor)
    probe.expectTerminated(actor)
  }

  
  "A local ServiceRegistry" should {
    "inform no service available on startup" in {
      
      ServiceRegistry.create()
      system.actorSelection("/user/WitsServiceRegistry") ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService"))
      
    }

    "register local service" in {
      
      system.actorSelection("/user/WitsServiceRegistry") ! RegisterService("MyService", 1, self)
      system.actorSelection("/user/WitsServiceRegistry") ! LocateService("MyService", 1)
      expectMsg(ServiceLocation("MyService", Set(self)))
      
    }

    "unregister when service dies" in {
      val probe = TestProbe()
      system.actorSelection("/user/WitsServiceRegistry") ! RegisterService("MyService", 1, probe.ref)
      poison(probe.ref)
      system.actorSelection("/user/WitsServiceRegistry") ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService"))
      
    }

    "inform local proxy when service become available" in {
      
      val probe = TestProbe()

      system.actorSelection("/user/WitsServiceRegistry") ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService"))

      system.actorSelection("/user/WitsServiceRegistry") ! RegisterService("MyService", 1, probe.ref)
      expectMsg(ServiceLocation("MyService", Set(probe.ref)))

      poison(probe.ref)
    }

    "inform local proxy when service become unavailable" in {
      
      val probe = TestProbe()

      system.actorSelection("/user/WitsServiceRegistry") ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService"))

      system.actorSelection("/user/WitsServiceRegistry") ! RegisterService("MyService", 1, probe.ref)
      expectMsg(ServiceLocation("MyService", Set(probe.ref)))

      poison(probe.ref)

      expectMsg(ServiceUnavailable("MyService"))
      
    }

    "inform local proxy when a new service register itself" in {
      
      val probe = TestProbe()
      val probe2 = TestProbe()

      system.actorSelection("/user/WitsServiceRegistry") ! RegisterService("MyService", 1, probe.ref)
      system.actorSelection("/user/WitsServiceRegistry") ! LocateService("MyService", 1)
      expectMsg(ServiceLocation("MyService", Set(probe.ref)))
      system.actorSelection("/user/WitsServiceRegistry") ! RegisterService("MyService", 1, probe2.ref)
      expectMsg(ServiceLocation("MyService", Set(probe.ref, probe2.ref)))
      poison(probe.ref)
      poison(probe2.ref)
    }
  }

  "A remote service registry" should {
    testConductor.enter("all-up")
    "Send remote service list to other registry when they join" in {
      runOn(node1) {
        val probe = TestProbe()
        system.actorSelection("/user/WitsServiceRegistry") ! RegisterService("MyService", 1, probe.ref)
        testConductor.enter("node1-with-service")
      }

      runOn(node2) {
        testConductor.enter("node1-with-service")
        Cluster(system) join node(node1).address
        system.actorSelection("/user/WitsServiceRegistry") ! LocateService("MyService", 1)
        expectMsgPF() {
          case ServiceLocation("MyService", _) => ()
        }
      }
    }
  }

}