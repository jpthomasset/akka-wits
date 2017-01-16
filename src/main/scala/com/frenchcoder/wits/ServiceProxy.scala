package com.frenchcoder.wits

import akka.actor.{ Actor, ActorLogging, ActorRef }
import scala.util.Random


class ServiceProxy[T <: ServiceTag](service: T, registry: ActorRef) extends Actor with ActorLogging {

  val serviceName = service.getClass.getName

  registry ! LocateService(serviceName, service.version)

  def receive: Receive = {
    case ServiceLocation(_, actors) if !actors.isEmpty => context.become(withRemoteServices(actors))

    case x:ServiceMessage => sender() ! ServiceUnavailable(serviceName)
  }

  def withRemoteServices(remoteServices: Set[ActorRef]): Receive = {

    case ServiceLocation(_, actors) =>
      if (!actors.isEmpty) context.become(withRemoteServices(actors))
      else context.become(receive)

    case x:ServiceMessage => getServiceActor(remoteServices).foreach(_ forward x)
  }

  def getServiceActor(remoteServices:Set[ActorRef]): Option[ActorRef] = Random.shuffle(remoteServices).headOption
}
