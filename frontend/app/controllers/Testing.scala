package controllers

import actions.GoogleAuthRequest
import com.gu.googleauth.UserIdentity
import com.typesafe.scalalogging.slf4j.LazyLogging
import play.api.mvc.{Controller, Cookie}
import play.twirl.api.Html
import utils.TestUsers.testUsers
import actions.Functions._

object Testing extends Controller with LazyLogging {

  val analyticsOffCookie = Cookie("ANALYTICS_OFF_KEY", "true", httpOnly = false)

  val AuthorisedTester = GoogleAuthenticatedStaffAction andThen isInAuthorisedGroupGoogle[UserIdentity, GoogleAuthRequest](
    Set("membership.dev@guardian.co.uk", "touchpoint@guardian.co.uk"),
      views.html.fragments.oauth.testUsersWrongGroup())

  def testUser = AuthorisedTester { implicit request =>
    val testUserString = testUsers.generate()
    logger.info(s"Generated test user string $testUserString for ${request.user.email}")
    Ok(views.html.testing.testUsers(testUserString)).withCookies(analyticsOffCookie)
  }

  def analyticsOff = CachedAction {
    Ok(s"${analyticsOffCookie.name} cookie dropped").withCookies(analyticsOffCookie)
  }


}
