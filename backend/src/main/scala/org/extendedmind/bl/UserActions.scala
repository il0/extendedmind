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

trait UserActions {

  def db: GraphDatabase
  def mailgun: MailgunClient
  def settings: Settings

  def actorRefFactory: ActorRefFactory
  implicit val implicitActorRefFactory = actorRefFactory
  implicit val implicitExecutionContext = actorRefFactory.dispatcher 
  
  def signUp(signUp: SignUp)(implicit log: LoggingContext): Response[SetResult] = {
    log.info("signUp: email {}", signUp.email)
    if (settings.signUpMode == MODE_ADMIN) 
      log.warning("CRITICAL: Making {} an administrator because extendedmind.security.signUpMode is set to ADMIN", 
          signUp.email)
    for {
      isUnique <- db.validateEmailUniqueness(signUp.email).right
      result <- db.putNewUser(User(signUp.email), signUp.password, settings.signUpMode).right
    } yield result
    
    // TODO: Send verification email as Future
  }
  
  def getPublicUser(email: String)(implicit log: LoggingContext): Response[PublicUser] = {
    log.info("getPublicUser: email {}", email)
    val user = db.getUser(email)
    if (user.isLeft) Left(user.left.get)
    else Right(PublicUser(user.right.get.uuid.get))
  }
   
  def getUser(userUUID: UUID)(implicit log: LoggingContext): Response[User] = {
    log.info("getUser: user {}", userUUID)
    db.getUser(userUUID)
  }
  
  def putUser(userUUID: UUID, user: User)(implicit log: LoggingContext): Response[SetResult] = {
    log.info("putUser: user {}", userUUID)
    db.putExistingUser(userUUID, user) match {
      case Right(result) => {
        Right(result._1)
        // TODO
        // if (result._2)
        //   SEND EMAIL CONFIRMATION TO NEW ADDRESS! 
      }
      case Left(e) => Left(e)
    }
  }
  
  def changeUserType(userUUID: UUID, userType: Integer)(implicit log: LoggingContext): Response[SetResult] = {
    log.info("changeUserType: user {} to type {}", userUUID, userType)
    db.changeUserType(userUUID, userType)
  }
  
}

class UserActionsImpl(implicit val implSettings: Settings, implicit val inj: Injector, 
                      implicit val implActorRefFactory: ActorRefFactory)
  extends UserActions with Injectable {
  override def settings  = implSettings
  override def db = inject[GraphDatabase]
  override def mailgun = inject[MailgunClient]
  override def actorRefFactory = implActorRefFactory
}
