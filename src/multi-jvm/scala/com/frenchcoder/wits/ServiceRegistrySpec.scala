package com.frenchcoder.wits

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ CurrentClusterState, MemberUp }
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
      
      ServiceRegistry.create
      ServiceRegistry.selectFromSystem ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService", 1))
      ServiceRegistry.selectFromSystem ! RemoveProxy(self)
      
    }

    "register local service and unregister when it dies" in {
      val probe = TestProbe()
      ServiceRegistry.selectFromSystem ! RegisterService("MyService", 1, probe.ref)
      ServiceRegistry.selectFromSystem ! LocateService("MyService", 1)
      expectMsg(ServiceLocation("MyService", 1, Set(probe.ref)))
      poison(probe.ref)
      expectMsg(ServiceUnavailable("MyService", 1))
      ServiceRegistry.selectFromSystem ! RemoveProxy(self)
    }


    "inform local proxy when service become available and unavailable" in {
      
      val probe = TestProbe()

      ServiceRegistry.selectFromSystem ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService", 1))

      ServiceRegistry.selectFromSystem ! RegisterService("MyService", 1, probe.ref)
      expectMsg(ServiceLocation("MyService", 1, Set(probe.ref)))

      poison(probe.ref)
      expectMsg(ServiceUnavailable("MyService", 1))

      ServiceRegistry.selectFromSystem ! RemoveProxy(self)
    }


    "inform local proxy when a new service register itself" in {
      
      val probe = TestProbe()
      val probe2 = TestProbe()

      ServiceRegistry.selectFromSystem ! RegisterService("MyService", 1, probe.ref)
      ServiceRegistry.selectFromSystem ! LocateService("MyService", 1)
      expectMsg(ServiceLocation("MyService", 1, Set(probe.ref)))
      ServiceRegistry.selectFromSystem ! RegisterService("MyService", 1, probe2.ref)
      expectMsg(ServiceLocation("MyService", 1, Set(probe.ref, probe2.ref)))
      poison(probe.ref)
      expectMsg(ServiceLocation("MyService", 1, Set(probe2.ref)))
      poison(probe2.ref)
      expectMsg(ServiceUnavailable("MyService", 1))
      ServiceRegistry.selectFromSystem ! RemoveProxy(self)

    }

  }
  "A remote service registry" should {
    testConductor.enter("ready-to-join")
    runOn(node1) {
      "register a service on node1" in {
     
        val probe = TestProbe()
        ServiceRegistry.selectFromSystem ! RegisterService("ServiceNode1", 1, probe.ref)
        
      }
    }

    
    "join cluster" in {
      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      val firstAddress = node(node1).address
      val secondAddress = node(node2).address

      Cluster(system) join firstAddress

      receiveN(2).collect { case MemberUp(m) => m.address }.toSet should be(
        Set(firstAddress, secondAddress))
      
      testConductor.enter("cluster-up")
    }

    runOn(node2) {
      "make service available on node2 when joining cluster" in {
    
        ServiceRegistry.selectFromSystem ! LocateService("ServiceNode1", 1)
        ignoreMsg {
          case ServiceUnavailable(_, _) => true
        }
        expectMsgPF() {
          case ServiceLocation("ServiceNode1", 1, _) => ()
        }
      }
    }

    runOn(node2) {
      "register a service on node 2" in {
        val probe = TestProbe()
        ServiceRegistry.selectFromSystem ! RegisterService("ServiceNode2", 1, probe.ref)
        testConductor.enter("service-node-2")
      }
    }

    runOn(node1) {
      "make service available on node 1" in {
        testConductor.enter("service-node-2")

        ServiceRegistry.selectFromSystem ! LocateService("ServiceNode2", 1)
        ignoreMsg {
          case ServiceUnavailable("ServiceNode2", 1) => println("got unavailable"); true
        }
        expectMsgPF() {
          case ServiceLocation("ServiceNode2", 1, _) => ()
        }
      }
    }

    "forward remote service address between nodes" in {
      runOn(node1) {
        val service = TestProbe()
        ServiceRegistry.selectFromSystem ! RegisterService("Service2Node1", 1, service.ref)
        testConductor.enter("service2-node1")
        service.expectMsg("Hello world!")
      }

      runOn(node2) {
        testConductor.enter("service2-node1")
        ServiceRegistry.selectFromSystem ! LocateService("Service2Node1", 1)
        ignoreMsg {
          case ServiceUnavailable("Service2Node1", 1) => true
        }
        expectMsgPF() {
          case ServiceLocation("Service2Node1", 1, locations) => locations.foreach(_ ! "Hello world!")
        }
      }
    }
  }

}
