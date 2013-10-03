package org.extendedmind.bl

import org.extendedmind.domain._
import org.extendedmind.db._
import org.extendedmind._
import org.extendedmind.email._
import org.extendedmind.security._
import org.extendedmind.Response._
import scaldi.Injector
import scaldi.Injectable
import org.extendedmind.db.EmbeddedGraphDatabase
import spray.util.LoggingContext
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext
import akka.actor.ActorRefFactory
import akka.actor.ActorSystem
import java.util.UUID

trait CollectiveActions {

  def db: GraphDatabase
  def settings: Settings
    
  def putNewCollective(creatorUUID: UUID, collective: Collective)(implicit log: LoggingContext): Response[SetResult] = {
    log.info("putNewCollective: creator {}", creatorUUID)
   
    if (settings.commonCollectives) 
      log.warning("CRITICAL: Making collective {} a common collective to all "
                 +"users because extendedmind.security.commonCollectives is set to true", collective.title)
    db.putNewCollective(creatorUUID, collective, settings.commonCollectives)
  }
  
  def putExistingCollective(collectiveUUID: UUID, collective: Collective)(implicit log: LoggingContext): Response[SetResult] = {
    log.info("putExistingCollective: collective {}", collectiveUUID)
    db.putExistingCollective(collectiveUUID, collective)
  }
  
  def getCollective(collectiveUUID: UUID)(implicit log: LoggingContext): Response[Collective] = {
    log.info("getCollective: collective {}", collectiveUUID)
    db.getCollective(collectiveUUID)
  }
}

class CollectiveActionsImpl(implicit val implSettings: Settings, implicit val inj: Injector)
  extends CollectiveActions with Injectable {
  override def settings  = implSettings
  override def db = inject[GraphDatabase]
}