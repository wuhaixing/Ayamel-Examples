package controllers

import authentication.Authentication
import play.api.mvc._
import models.{AddCourseRequest, User, Content, Course}
import service.{TimeTools, LMSAuth}
import anorm.NotAssigned

/**
 * This controller manages all the pages relating to courses, including authentication.
 */
object Courses extends Controller {

  /**
   * Gets the course. A mix-in for action composition.
   * @param id The id of the course
   * @param f The action body. Returns a result
   * @param request The implicit http request
   * @return A result
   */
  def getCourse(id: Long)(f: Course => Result)(implicit request: Request[_]): Result = {
    val course = Course.findById(id)
    if (course.isDefined)
      f(course.get)
    else
      Errors.notFound
  }


  /**
   * The lti authentication page. Redirects to the course page if successful.
   */
  def ltiAuth(id: Long) = Action(parse.tolerantText) {
    implicit request =>
      getCourse(id) {
        course =>
          val user = LMSAuth.ltiAuth(course)
          if (user.isDefined)
            Redirect(routes.Courses.view(id)).withSession("userId" -> user.get.id.get.toString)
          else
            Errors.forbidden
      }
  }

  /**
   * The key-based authentication page. Redirects to the course page if successful.
   */
  def keyAuth(id: Long) = Action {
    implicit request =>
      getCourse(id) {
        course =>
          val user = LMSAuth.keyAuth(course)
          if (user.isDefined)
            Redirect(routes.Courses.view(id)).withSession("userId" -> user.get.id.get.toString)
          else
            Errors.forbidden
      }
  }

  /**
   * The course page.
   */
  def view(id: Long) = Authentication.authenticatedAction() {
    implicit request =>
      implicit user =>
        getCourse(id) {
          course =>
            if (user canView course)
              Ok(views.html.courses.view(course))
            else
              Redirect(routes.Courses.courseRequestPage(id))
        }
  }

  def addContent(id: Long) = Authentication.authenticatedAction(parse.urlFormEncoded) {
    implicit request =>
      implicit user =>
        getCourse(id) {
          course =>

          // Only non-guest members and admins can add content
            if (user canAddContentTo course) {

              // Add the content to the course
              val contentId = request.body("addContent")(0).toLong
              val content = Content.findById(contentId)
              if (content.isDefined) {
                course.addContent(content.get)
                Redirect(routes.Courses.view(id)).flashing("success" -> "Content added to course.")
              } else
                Errors.notFound
            } else
              Errors.forbidden
        }
  }

  def addAnnouncement(id: Long) = Authentication.authenticatedAction(parse.urlFormEncoded) {
    implicit request =>
      implicit user =>
        getCourse(id) {
          course =>

          // Only non-guest members and admins can add content
            if (user canAddContentTo course) {

              // Add the content to the course
              val announcement = request.body("announcement")(0)
              course.makeAnnouncement(user, announcement)
              Redirect(routes.Courses.view(id)).flashing("success" -> "Announcement published.")
            } else
              Errors.forbidden
        }
  }

  def create = Authentication.authenticatedAction(parse.urlFormEncoded) {
    request =>
      user =>

      // Check if the user is allowed to create a course
        if (user.canCreateCourse) {

          // Collect info
          val courseName = request.body("courseName")(0)
          val startDate = request.body("startDate")(0)
          val endDate = request.body("endDate")(0)

          // Create the course
          val course = Course(NotAssigned, courseName, TimeTools.parseDate(startDate), TimeTools.parseDate(endDate)).save
          user.enroll(course, teacher = true)

          // Redirect to the course page
          Redirect(routes.Courses.view(course.id.get)).flashing("success" -> "Course Added")
        } else
          Errors.forbidden
  }

  def createPage = Authentication.authenticatedAction() {
    implicit request =>
      implicit user =>

      // Check if the user is allowed to create a course
        if (user.canCreateCourse)
          Ok(views.html.courses.create())
        else
          Errors.forbidden
  }

  def list = Authentication.authenticatedAction() {
    implicit request =>
      implicit user =>

      // Guests cannot browse
        Authentication.enforceNotRole(User.roles.guest) {
          val courses = Course.list
          Ok(views.html.courses.list(courses))
        }
  }

  def courseRequestPage(id: Long) = Authentication.authenticatedAction() {
    implicit request =>
      implicit user =>
        getCourse(id) {
          course =>

            // Guests cannot request courses
            Authentication.enforceNotRole(User.roles.guest) {
              val findRequest = AddCourseRequest.listByCourse(course).find(req => req.userId == user.id.get)
              if (findRequest.isDefined)
                Ok(views.html.courses.pending(course))
              else
                Ok(views.html.courses.request(course))
            }
        }
  }

  def submitCourseRequest(id: Long) = Authentication.authenticatedAction(parse.urlFormEncoded) {
    implicit request =>
      implicit user =>
        getCourse(id) {
          course =>

            // Make sure it's not a guest
            Authentication.enforceNotRole(User.roles.guest) {
              val message = request.body("message")(0)
              AddCourseRequest(NotAssigned, user.id.get, course.id.get, message).save

              // Notify the teachers
              val notificationMessage = "A student has requested to join your course \"" + course.name + "\"."
              course.getTeachers.foreach { _.sendNotification(notificationMessage)}

              Ok(views.html.courses.pending(course))
            }
        }
  }

  def approvePage(id: Long) = Authentication.authenticatedAction() {
    implicit request =>
      implicit user =>
        getCourse(id) {
          course =>

            if (user canEdit course)
              Ok(views.html.courses.approveRequests(course))
            else
              Errors.forbidden
        }
  }

  def approveRequest(id: Long, requestId: Long) = Authentication.authenticatedAction() {
    implicit request =>
      implicit user =>
        getCourse(id) {
          course =>

            // Get the request
            val courseRequest = AddCourseRequest.findById(requestId)
            if (courseRequest.isDefined) {

              // Make sure the user is allowed to approve
              if(user.canApprove(courseRequest.get, course)) {
                courseRequest.get.approve()
                Redirect(routes.Courses.approvePage(course.id.get)).flashing("info" -> "Course request approved")
              } else
                Errors.forbidden
            } else
              Errors.notFound
        }
  }

  def denyRequest(id: Long, requestId: Long) = Authentication.authenticatedAction() {
    implicit request =>
      implicit user =>
        getCourse(id) {
          course =>

            // Get the request
            val courseRequest = AddCourseRequest.findById(requestId)
            if (courseRequest.isDefined) {

              // Make sure the user is allowed to approve
              if(user.canApprove(courseRequest.get, course)) {
                courseRequest.get.deny()
                Redirect(routes.Courses.approvePage(course.id.get)).flashing("info" -> "Course request denied")
              } else
                Errors.forbidden
            } else
              Errors.notFound
        }
  }


}
