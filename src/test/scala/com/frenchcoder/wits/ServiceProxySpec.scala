package com.frenchcoder.wits

package com.frenchcoder.wits

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

class ServiceProxySpec extends TestKit(ActorSystem("ServiceProxySpec")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  case class FakeServiceTag(version: Int = 1) extends ServiceTag
  case class FakeServiceMessage(msg: String) extends ServiceMessage

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A ServiceProxy" should {
    "request service location upon startup" in {
      val registry = TestProbe()
      
      val fakeProxy = system.actorOf(Props(new ServiceProxy(FakeServiceTag())))

      registry.expectMsg(LocateService(classOf[FakeServiceTag].getName, FakeServiceTag().version))
      
    }

    "forward ServiceMessage to backend service" in {
      val registry = TestProbe()
      val service = TestProbe()
      
      val fakeProxy = system.actorOf(Props(new ServiceProxy(FakeServiceTag())))

      fakeProxy ! ServiceLocation(classOf[FakeServiceTag].getName, Set(service.ref))
      fakeProxy ! FakeServiceMessage("Hello World!")
      service.expectMsg(FakeServiceMessage("Hello World!"))
      
    }

  }
}
