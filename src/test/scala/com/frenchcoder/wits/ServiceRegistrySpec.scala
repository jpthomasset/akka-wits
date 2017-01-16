package com.frenchcoder.wits

import akka.actor.{ ActorRef, ActorSystem, PoisonPill, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

class ServiceRegistrySpec extends TestKit(ActorSystem("ServiceRegistrySpec")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  def poison(actor: ActorRef) = {
    val probe = TestProbe()
    probe.watch(actor)
    system.stop(actor)
    probe.expectTerminated(actor)
  }

  "A ServiceRegistry" should {
    "inform no service available on startup" in {
      val registry = system.actorOf(Props[ServiceRegistry])
      registry ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService"))
      poison(registry)
    }

    "register local service" in {
      val registry = system.actorOf(Props[ServiceRegistry])
      registry ! RegisterService("MyService", 1, self)
      registry ! LocateService("MyService", 1) 
      expectMsg(ServiceLocation("MyService", Set(self)))
      poison(registry)
      
    }

    "unregister when service dies" in {
      val registry = system.actorOf(Props[ServiceRegistry])
      val probe = TestProbe()
      registry ! RegisterService("MyService", 1, probe.ref)
      poison(probe.ref)
      registry ! LocateService("MyService", 1) 
      expectMsg(ServiceUnavailable("MyService"))
      poison(registry)
    }

    "inform local proxy when service become available" in {
      val registry = system.actorOf(Props[ServiceRegistry])
      val probe = TestProbe()

      registry ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService"))

      registry ! RegisterService("MyService", 1, probe.ref)
      expectMsg(ServiceLocation("MyService", Set(probe.ref)))
      poison(registry)
    }

    "inform local proxy when service become unavailable" in {
      val registry = system.actorOf(Props[ServiceRegistry])
      val probe = TestProbe()

      registry ! LocateService("MyService", 1)
      expectMsg(ServiceUnavailable("MyService"))

      registry ! RegisterService("MyService", 1, probe.ref)
      expectMsg(ServiceLocation("MyService", Set(probe.ref)))

      poison(probe.ref)

      expectMsg(ServiceUnavailable("MyService"))
      poison(registry)
    }

    "inform local proxy when a new service register itself" in {
      val registry = system.actorOf(Props[ServiceRegistry])
      val probe = TestProbe()
      val probe2 = TestProbe()

      registry ! RegisterService("MyService", 1, probe.ref)
      registry ! LocateService("MyService", 1)
      expectMsg(ServiceLocation("MyService", Set(probe.ref)))
      registry ! RegisterService("MyService", 1, probe2.ref)
      expectMsg(ServiceLocation("MyService", Set(probe.ref, probe2.ref)))
      poison(registry)
    }
  }
}
