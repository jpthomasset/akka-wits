package com.frenchcoder.wits

trait ServiceRegistryConfig {
  def getActorName() : String
}

object ServiceRegistryDefaultConfig extends ServiceRegistryConfig {
  val getActorName = "WitsServiceRegistry"
}
