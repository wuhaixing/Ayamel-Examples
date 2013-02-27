package controllers.authentication

import play.api.mvc.{Action, Controller}
import play.api.libs.openid.OpenID
import concurrent.ExecutionContext
import ExecutionContext.Implicits.global

object Google extends Controller {

  def login = Action {
    implicit request =>
      val openID = "https://www.google.com/accounts/o8/id"

      Async {
        OpenID.redirectURL(
          openID, routes.Google.callback().absoluteURL(),
          Seq(
            "email" -> "http://axschema.org/contact/email",
            "firstname" -> "http://axschema.org/namePerson/first",
            "lastname" -> "http://axschema.org/namePerson/last"
          )
        ).map(Redirect(_))
      }
  }

  def callback = Action {
    implicit request =>
      Async {
        OpenID.verifiedId.map(userInfo => {
          // Get the user info
          val username = userInfo.id
          val firstName = userInfo.attributes("firstname")
          val lastName = userInfo.attributes("lastname")
          val email = userInfo.attributes("email")
          logic.Authentication.loginGoogle(username, firstName, lastName, email)
        })
      }
  }

}