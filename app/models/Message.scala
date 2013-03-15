package models

import anorm.{NotAssigned, ~, Pk}
import sqlTraits.{SQLSelectable, SQLDeletable, SQLSavable}
import anorm.SqlParser._
import play.api.db.DB
import play.api.Play.current
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

/**
 * A message can be communication between users, an announcement, notification, or course request
 * @param id The id of the message
 * @param from The id of the sender of the message
 * @param to The id of the receiver of the message
 * @param messageType The type of message (message, notification, request, announcement)
 * @param content The content of the message
 * @param date When it was sent
 * @param read Has the receiver read it?
 */
case class Message(id: Pk[Long], from: Long, to: Long, messageType: Symbol, content: String, date: String,
                   read: Boolean = false) extends SQLSavable with SQLDeletable {

  /**
   * Saves the message to the DB
   * @return The possibly updated message
   */
  def save: Message = {
    if (id.isDefined) {
      update(Message.tableName, 'id -> id, 'fromUser -> from, 'toUser -> to, 'messageType -> messageType.name,
        'content -> content, 'messageDate -> date, 'messageRead -> read)
      this
    } else {
      val id = insert(Message.tableName, 'fromUser -> from, 'toUser -> to, 'messageType -> messageType.name,
        'content -> content, 'messageDate -> date, 'messageRead -> read)
      this.copy(id)
    }
  }

  /**
   * Deletes the message from the DB
   */
  def delete() {
    delete(Content.tableName, id)
  }

  //      Logic
  // ===============

  /**
   * Marks the message as read
   * @return The updated message
   */
  def markAsRead: Message = this.copy(read = true).save

  /**
   * Add the user to the course, notify the user, and delete the request.
   * If the message is not a request then it does nothing.
   */
  def approve() {
    if (this.messageType == 'request) {

      // Add the user to the course
      val user = User.findById(this.from).get
      val course = Course.findById(this.to).get
      user.enroll(course, teacher = false)

      // Notify the student
      Message.sendNotification(user, "You have been accepted into the course " + course.name)

      // Delete the request
      this.delete()
    }
  }

  /**
   * Deny admittance to the course: notify the user, and delete the request.
   * If the message is not a request then it does nothing.
   */
  def deny() {
    if (this.messageType == 'request) {

      // Notify the student
      val user = User.findById(this.from).get
      val course = Course.findById(this.to).get
      Message.sendNotification(user, "You have been denied admittance into the course " + course.name)

      // Delete the request
      this.delete()
    }
  }

}

object Message extends SQLSelectable[Message] {
  val tableName = "message"

  val simple = {
    get[Pk[Long]](tableName + ".id") ~
      get[Long](tableName + ".fromUser") ~
      get[Long](tableName + ".toUser") ~
      get[String](tableName + ".messageType") ~
      get[String](tableName + ".content") ~
      get[String](tableName + ".messageDate") ~
      get[Boolean](tableName + ".messageRead") map {
      case id~from~to~messageType~content~date~messageRead =>
        Message(id, from, to, Symbol(messageType), content, date, messageRead)
    }
  }

  /**
   * Finds a message by its id
   * @param id The id of the message
   * @return If a message was found, then Some[Message], otherwise None
   */
  def findById(id: Long): Option[Message] = findById(tableName, id, simple)

  /**
   * Lists all messages
   * @return The list of messages
   */
  def list: List[Message] = list(tableName, simple)

  /**
   * Gets all messages that belong to a certain user
   * @param user The user for whom the messages will be
   * @return The list of messages
   */
  def listByUser(user: User): List[Message] =
    DB.withConnection {
      implicit connection =>
        anorm.SQL("select * from " + tableName + " where toUser = {toUser}").on('toUser -> user.id).as(simple *)
    }

  /**
   * Gets all course requests for a certain course
   * @param course The course for which the requests will be listed
   * @return The list of messages
   */
  def listClassRequests(course: Course): List[Message] =
    DB.withConnection {
      implicit connection =>
        anorm.SQL("select * from " + tableName + " where toUser = {toUser} and messageType = {messageType}")
          .on('toUser -> course.id, 'messageType -> "request").as(simple *)
    }

  /**
   * Gets all announcements for a certain course
   * @param course The course for which the announcements will be listed
   * @return The list of announcements
   */
  def listClassAnnouncements(course: Course): List[Message] =
    DB.withConnection {
      implicit connection =>
        anorm.SQL("select * from " + tableName + " where toUser = {toUser} and messageType = {messageType}")
          .on('toUser -> course.id, 'messageType -> "announcement").as(simple *)
    }

  /**
   * Send a message from one user to another
   * @param fromUser The user who is sending
   * @param toUser The user who is receiving
   * @param message The content of the message
   * @return The sent message
   */
  def sendMessage(fromUser: User, toUser: User, message: String): Message = {
    val time = ISODateTimeFormat.dateTime().print(new DateTime())
    Message(NotAssigned, fromUser.id.get, toUser.id.get, 'message, message, time).save
  }

  /**
   * Send a notification to a user
   * @param user The user receiving the notification
   * @param message The content of the notification
   * @return The sent notification
   */
  def sendNotification(user: User, message: String): Message = {
    val time = ISODateTimeFormat.dateTime().print(new DateTime())
    Message(NotAssigned, -1, user.id.get, 'notification, message, time).save
  }

  /**
   * Make an announcement to a course
   * @param user The user who is making the announcement
   * @param course The course where the announcement will be made
   * @param message The content of the announcement
   * @return The sent message
   */
  def sendAnnouncement(user: User, course: Course, message: String): Message = {
    val time = ISODateTimeFormat.dateTime().print(new DateTime())
    Message(NotAssigned, user.id.get, course.id.get, 'announcement, message, time).save
  }

  /**
   * Send a request for a user to join a course.
   * @param user The user wanting to join
   * @param course The course to join
   * @param message The user can add a message
   * @return The sent request
   */
  def sendRequest(user: User, course: Course, message: String): Message = {
    // Send the teachers a notification
    val message = user.name + " has requested to join the course " + course.name
    course.getTeachers.foreach(teacher => {
      sendNotification(teacher, message)
    })

    val time = ISODateTimeFormat.dateTime().print(new DateTime())
    Message(NotAssigned, user.id.get, course.id.get, 'request, message, time).save
  }
}