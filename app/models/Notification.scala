package models

import anorm.{~, Pk}
import service.TimeTools
import dataAccess.sqlTraits.{SQLSelectable, SQLDeletable, SQLSavable}
import anorm.SqlParser._
import play.api.db.DB
import play.api.Play.current

/**
 * A notification to a user
 * @param id The notification id
 * @param userId The id of the user the notification is to
 * @param message The content/message of the notification
 * @param dateSent When it was sent
 */
case class Notification(id: Pk[Long], userId: Long, message: String, dateSent: String = TimeTools.now(),
                        messageRead: Boolean = false) extends SQLSavable with SQLDeletable {

  /**
   * Saves the notification to the DB
   * @return The possibly modified notification
   */
  def save: Notification = {
    if (id.isDefined) {
      update(Notification.tableName, 'id -> id, 'userId -> userId, 'message -> message, 'dateSent -> dateSent,
        'messageRead -> messageRead)
      this
    } else {
      val id = insert(Notification.tableName, 'userId -> userId, 'message -> message, 'dateSent -> dateSent,
        'messageRead -> messageRead)
      this.copy(id)
    }
  }

  /**
   * Deletes the notification from the DB
   */
  def delete() {
    delete(Notification.tableName, id)
  }

}


object Notification extends SQLSelectable[Notification] {
  val tableName = "notification"

  val simple = {
    get[Pk[Long]](tableName + ".id") ~
      get[Long](tableName + ".userId") ~
      get[String](tableName + ".message") ~
      get[String](tableName + ".dateSent") ~
      get[Boolean](tableName + ".messageRead") map {
      case id~userId~reason~dateSent~messageRead => Notification(id, userId, reason, dateSent, messageRead)
    }
  }

  /**
   * Finds a notification by the id
   * @param id The id of the membership
   * @return If a notification was found, then Some[Notification], otherwise None
   */
  def findById(id: Long): Option[Notification] = findById(tableName, id, simple)

  /**
   * Lists a user's notifications
   * @param user The user who made the notification belong to
   * @return The list of notifications
   */
  def listByUser(user: User): List[Notification] =
    DB.withConnection {
      implicit connection =>
        anorm.SQL("select * from " + tableName + " where userId = {id}").on('id -> user.id).as(simple *)
    }

  /**
   * Lists all notifications
   * @return The list of notifications
   */
  def list: List[Notification] = list(tableName, simple)
}