package com.frenchcoder.wits

import akka.actor.{ Actor, ActorRef }


trait ServiceTag {
  def version: Int
}

trait ServiceMessage

abstract class ServiceActor[T <: ServiceTag](tag: T, registryName: String = "WitsServiceRegistry") extends Actor {
  context.actorSelection(self.path.root / "user" / registryName) ! RegisterService(tag.getClass.getName, tag.version, self)
}
