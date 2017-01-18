package com.frenchcoder.wits

import akka.actor.{ Actor }


trait ServiceTag {
  def version: Int
}

trait ServiceMessage

abstract class ServiceActor[T <: ServiceTag](tag: T)(implicit config: ServiceRegistryConfig = ServiceRegistryDefaultConfig) extends Actor {
  ServiceRegistry.select ! RegisterService(tag.getClass.getName, tag.version, self)
}
