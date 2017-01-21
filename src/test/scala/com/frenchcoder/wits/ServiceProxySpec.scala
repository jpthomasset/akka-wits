package com.frenchcoder.wits

package com.frenchcoder.wits

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActors, TestKit, TestProbe }
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

class ServiceProxySpec extends TestKit(ActorSystem("ServiceProxySpec")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  case class FakeServiceTag(version: Int = 1) extends ServiceTag
  case class FakeServiceMessage(msg: String) extends ServiceMessage

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A ServiceProxy" should {
    val registry = TestProbe()
    val forward = system.actorOf(TestActors.forwardActorProps(registry.ref), ServiceRegistryDefaultConfig.getActorName)

    "request service location upon startup and inform registry when it exits" in {
      
      val fakeProxy = system.actorOf(Props(new ServiceProxy(FakeServiceTag())))

      registry.expectMsg(LocateService(classOf[FakeServiceTag].getName, FakeServiceTag().version))
      system.stop(fakeProxy)
      registry.expectMsg(RemoveProxy(fakeProxy))
    }

    "forward ServiceMessage to backend service" in {
      val service = TestProbe()
      
      val fakeProxy = system.actorOf(Props(new ServiceProxy(FakeServiceTag())))

      fakeProxy ! ServiceLocation(classOf[FakeServiceTag].getName, FakeServiceTag().version, Set(service.ref))
      fakeProxy ! FakeServiceMessage("Hello World!")
      service.expectMsg(FakeServiceMessage("Hello World!"))

      system.stop(fakeProxy)
    }

    "inform when service become unavailable" in {
      val service = TestProbe()
      
      val fakeProxy = system.actorOf(Props(new ServiceProxy(FakeServiceTag())))

      fakeProxy ! ServiceLocation(classOf[FakeServiceTag].getName, FakeServiceTag().version, Set(service.ref))
      fakeProxy ! ServiceUnavailable(classOf[FakeServiceTag].getName, FakeServiceTag().version)
      fakeProxy ! FakeServiceMessage("Hello World!")
      expectMsg(ServiceUnavailable(classOf[FakeServiceTag].getName, FakeServiceTag().version))

      system.stop(fakeProxy)
    }

  }
}
