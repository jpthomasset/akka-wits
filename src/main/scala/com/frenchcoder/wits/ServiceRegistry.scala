package com.frenchcoder.wits

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated }

sealed trait ServiceRegistryMessages
case class RegisterService(name: String, version: Int, actorRef: ActorRef, isLocal: Boolean = true) extends ServiceRegistryMessages
case class LocateService(name: String, version: Int) extends ServiceRegistryMessages
case class ServiceUnavailable(name: String) extends ServiceRegistryMessages
case class ServiceLocation(name: String, locations: Set[ActorRef]) extends ServiceRegistryMessages

object ServiceRegistry {
  def create(name: String = "WitsServiceRegistry")(implicit system: ActorSystem): ActorRef = system.actorOf(Props(new ServiceRegistry(name)), name)
}

private[wits] class ServiceRegistry(name: String) extends Actor with ActorLogging {
  
  case class ServiceDescription(name: String, version: Int, actorRef: ActorRef, isLocal: Boolean = true)
  case class ServiceName(name: String, version: Int)
  type ServiceMap = Set[ServiceDescription]

  //val cluster = Cluster(context.system)

  log.debug(s"Starting ServiceRegistry $self")
  context.become(withServices(Set.empty, Set.empty))

  def receive = Actor.emptyBehavior

  def withServices(services: ServiceMap, proxies: ServiceMap): Receive = {
    case LocateService(name, version) =>
      val l = getServiceLocations(services, name, version)
      if(l.isEmpty) sender() ! ServiceUnavailable(name)
      else sender() ! ServiceLocation(name, l)
      context.become(withServices(services, proxies + ServiceDescription(name, version, sender())))

    case RegisterService(name, version, actorRef, isLocal) =>
      context.watch(actorRef)
      val newServices = services + ServiceDescription(name, version, actorRef, isLocal)
      getServiceLocations(proxies, name, version).foreach(_ ! ServiceLocation(name, getServiceLocations(newServices, name, version)))
      context.become(withServices(newServices, proxies))

    case Terminated(a) =>
      val newServices = removeService(services, a)
      for( related <- getRelatedService(services, a) ;
        relatedProxy <- getServiceLocations(proxies, related.name, related.version)
      ) {
        val newLocations = getServiceLocations(newServices, related.name, related.version)
        if (newLocations.isEmpty)
          relatedProxy ! ServiceUnavailable(related.name)
        else
          relatedProxy ! ServiceLocation(related.name, getServiceLocations(newServices, related.name, related.version))
      }
      context.become(withServices(newServices, proxies))
  }


  private def removeService(services: ServiceMap, actorRef: ActorRef): ServiceMap = {
    services.filterNot(_.actorRef == actorRef)
  }

  private def getRelatedService(services: ServiceMap, actorRef: ActorRef): Set[ServiceName] = {
    services.filter(_.actorRef == actorRef)
      .map(s => ServiceName(s.name, s.version))
  }

  private def getServiceLocations(services: ServiceMap, name: String, version: Int): Set[ActorRef] = {
    services.filter(s => s.name == name && s.version == version).map(_.actorRef)
  }

}
