package com.frenchcoder.wits

package com.frenchcoder.wits

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

class ServiceSpec extends TestKit(ActorSystem("ServiceSpec")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  case class FakeServiceTag(version: Int = 1) extends ServiceTag
  class FakeService(registry: ActorRef) extends ServiceActor(registry, FakeServiceTag()) {
    def receive = Actor.emptyBehavior
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A Service" should {
    "register itself upon startup" in {
      val registry = TestProbe()
      
      val fakeService = system.actorOf(Props(new FakeService(registry.ref)))

      registry.expectMsg(RegisterService(classOf[FakeServiceTag].getName, FakeServiceTag().version, fakeService))
      
    }

  }
}
