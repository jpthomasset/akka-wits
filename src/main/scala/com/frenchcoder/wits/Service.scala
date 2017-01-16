package com.frenchcoder.wits

import akka.actor.{ Actor, ActorRef }


trait ServiceTag {
  def version: Int
}

trait ServiceMessage

abstract class ServiceActor[T <: ServiceTag](registry: ActorRef, tag: T) extends Actor {
  registry ! RegisterService(tag.getClass.getName, tag.version, self)
}
