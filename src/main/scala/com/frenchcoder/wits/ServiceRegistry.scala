package com.frenchcoder.wits

import akka.actor.{ Actor, ActorContext, ActorLogging, ActorPath, ActorRef, ActorSelection, ActorSystem, Props, RootActorPath, Terminated }
import akka.cluster.{ Cluster, Member }
import akka.cluster.ClusterEvent.MemberUp

sealed trait ServiceRegistryMessages
case class RegisterService(name: String, version: Int, actorRef: ActorRef, isLocal: Boolean = true) extends ServiceRegistryMessages
case class LocateService(name: String, version: Int) extends ServiceRegistryMessages
case class RemoveProxy(actorRef: ActorRef) extends ServiceRegistryMessages
case class ServiceUnavailable(name: String, version: Int) extends ServiceRegistryMessages
case class ServiceLocation(name: String, version: Int, locations: Set[ActorRef]) extends ServiceRegistryMessages

object ServiceRegistry {
  def select(implicit context: ActorContext, config: ServiceRegistryConfig = ServiceRegistryDefaultConfig): ActorSelection =
    context.actorSelection(getPath(context.system, config))

  def selectFromSystem(implicit system: ActorSystem, config: ServiceRegistryConfig = ServiceRegistryDefaultConfig): ActorSelection =
    system.actorSelection(getPath)

  def getPath(implicit system: ActorSystem, config: ServiceRegistryConfig = ServiceRegistryDefaultConfig): ActorPath =
    system / config.getActorName

  def create(implicit system: ActorSystem, config: ServiceRegistryConfig = ServiceRegistryDefaultConfig): ActorRef =
    system.actorOf(Props(new ServiceRegistry(config.getActorName)), config.getActorName)
}

private[wits] class ServiceRegistry(name: String) extends Actor with ActorLogging {
  
  case class ServiceDescription(name: String, version: Int, actorRef: ActorRef, isLocal: Boolean = true)
  case class ServiceName(name: String, version: Int)
  type ServiceMap = Set[ServiceDescription]

  log.debug(s"Starting ServiceRegistry $self")

  val cluster = Cluster(context.system)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  context.become(withServices(Set.empty, Set.empty, Set.empty))

  def receive = Actor.emptyBehavior

  def withServices(services: ServiceMap, proxies: ServiceMap, members: Set[Member]): Receive = {
    case LocateService(name, version) =>
      val l = getServiceLocations(services, name, version)

      if(l.isEmpty) sender() ! ServiceUnavailable(name, version)
      else sender() ! ServiceLocation(name, version, l)

      context.become(withServices(services, proxies + ServiceDescription(name, version, sender()), members))

    case RegisterService(name, version, actorRef, isLocal) =>
      context.watch(actorRef)
      val newServices = services + ServiceDescription(name, version, actorRef, isLocal)
      getServiceLocations(proxies, name, version).foreach(_ ! ServiceLocation(name, version, getServiceLocations(newServices, name, version)))
      if(isLocal) {
        members.foreach(m => remoteRegistry(m) ! RegisterService(name, version, actorRef, false))
      }
      context.become(withServices(newServices, proxies, members))

    case RemoveProxy(actor) =>
      context.become(withServices(services, removeService(proxies, actor), members))

    case Terminated(a) =>
      
      val newServices = removeService(services, a)
      for( related <- getRelatedService(services, a) ;
        relatedProxy <- getServiceLocations(proxies, related.name, related.version)
      ) {
        val newLocations = getServiceLocations(newServices, related.name, related.version)
        if (newLocations.isEmpty) 
          relatedProxy ! ServiceUnavailable(related.name, related.version)
        else 
          relatedProxy ! ServiceLocation(related.name, related.version, newLocations)
      }
      context.become(withServices(newServices, proxies, members))

    case MemberUp(m) =>
      val reg = remoteRegistry(m)

      services.filter(_.isLocal == true).foreach(s => reg ! RegisterService(s.name, s.version, s.actorRef, false))
      context.become(withServices(services, proxies, members + m))

  }

  private def remoteRegistry(m: Member): ActorSelection = context.actorSelection(RootActorPath(m.address) / "user" / name)

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
