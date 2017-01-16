package com.frenchcoder.wits

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated }

sealed trait ServiceRegistryMessages
case class RegisterService(name: String, version: Int, actorRef: ActorRef) extends ServiceRegistryMessages
case class LocateService(name: String, version: Int) extends ServiceRegistryMessages
case class ServiceUnavailable(name: String) extends ServiceRegistryMessages
case class ServiceLocation(name: String, locations: Set[ActorRef]) extends ServiceRegistryMessages

object ServiceRegistry {  
  def createRegistry(name: String = "WitsServiceRegistry")(implicit system: ActorSystem): ActorRef = system.actorOf(Props(new ServiceRegistry()), name)
}

private[wits] class ServiceRegistry() extends Actor with ActorLogging {
  type ServiceMap = Map[String, Map[Int, Set[ActorRef]]]
  case class ServiceName(name: String, version: Int)

  //val cluster = Cluster(context.system)

  log.debug(s"Starting ServiceRegistry $self")
  context.become(withServices(Map.empty, Map.empty))

  def receive = Actor.emptyBehavior

  def withServices(services: ServiceMap, proxies: ServiceMap): Receive = {
    case LocateService(name, version) =>
      val l = getServiceLocations(services, name, version)
      if(l.isEmpty) sender() ! ServiceUnavailable(name)
      else sender() ! ServiceLocation(name, l)
      context.become(withServices(services, addService(proxies, name, version, sender())))

    case RegisterService(name, version, actorRef) =>
      context.watch(actorRef)
      val newServices = addService(services, name, version, actorRef)
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

  private def addService(services: ServiceMap, name: String, version: Int, actorRef: ActorRef) : ServiceMap = {
    val thatService = services.getOrElse(name, Map.empty)
    val thatVersion = thatService.getOrElse(version, Set.empty) + actorRef
    services + (name -> (thatService + (version -> thatVersion)))
  }

  private def removeService(services: ServiceMap, actorRef: ActorRef): ServiceMap = {
    services.mapValues(s => s.mapValues(a => a.filterNot(_ == actorRef)))
  }

  private def getRelatedService(services: ServiceMap, actorRef: ActorRef): Set[ServiceName] = {
    services
      .mapValues(s => s.mapValues(a => a.filter(_ == actorRef)))
      .flatMap(s => s._2.map(v => ServiceName(s._1, v._1)))
      .toSet
  }

  private def getServiceLocations(services: ServiceMap, name: String, version: Int): Set[ActorRef] = {
    services.get(name).flatMap(_.get(version)).getOrElse(Set.empty)
  }

}
