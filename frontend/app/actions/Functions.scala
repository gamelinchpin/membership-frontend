package actions

import actions.Fallbacks._
import com.gu.googleauth.{GoogleGroupChecker, UserIdentity}
import com.gu.membership.salesforce.PaidMember
import com.gu.membership.util.Timing
import com.gu.monitoring.CloudWatch
import com.typesafe.scalalogging.slf4j.LazyLogging
import configuration.Config
import controllers.IdentityRequest
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Security.{AuthenticatedRequest, AuthenticatedBuilder}
import play.api.mvc._
import play.twirl.api.Html
import services.{AuthenticationService, IdentityService}

import scala.concurrent.Future

/**
 * These ActionFunctions serve as components that can be composed to build the
 * larger, more-generally useful pipelines in 'CommonActions'.
 *
 * https://www.playframework.com/documentation/2.3.x/ScalaActionsComposition
 */
object Functions extends LazyLogging {

  val googleGroupChecker = new GoogleGroupChecker(Config.googleGroupCheckerAuthConfig)

  def resultModifier(f: Result => Result) = new ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = block(request).map(f)
  }

  def authenticated(onUnauthenticated: RequestHeader => Result = chooseSigninOrRegister(_)): ActionBuilder[AuthRequest] =
    new AuthenticatedBuilder(AuthenticationService.authenticatedUserFor(_), onUnauthenticated)


  def memberRefiner(onNonMember: RequestHeader => Result = notYetAMemberOn(_)) =
    new ActionRefiner[AuthRequest, AnyMemberTierRequest] {
      override def refine[A](request: AuthRequest[A]) = request.forMemberOpt {
        _.map(member => MemberRequest(member, request)).toRight(onNonMember(request))
      }
    }

  def onlyNonMemberFilter(onPaidMember: RequestHeader => Result = changeTier(_)) = new ActionFilter[AuthRequest] {
    override def filter[A](request: AuthRequest[A]) = request.forMemberOpt(_.map(_ => onPaidMember(request)))
  }


  def isInAuthorisedGroupGoogle[U, R[_] <: AuthenticatedRequest[_,U]]
  (includedGroups: Set[String],errorWhenNotInAcceptedGroups: Html)(implicit g: GoogleAuthed[U]) = new ActionFilter[R] {
    override def filter[A](request: R[A]) = {
      val email = g.googleUserIn(request.user).email
      for (usersGroups <- googleGroupChecker.retrieveGroupsFor(email)) yield {
        if (includedGroups.intersect(usersGroups).nonEmpty) None else {
          logger.info(s"Excluding $email from '${request.path}' - not in accepted groups: $includedGroups")
          Some(unauthorisedStaff(errorWhenNotInAcceptedGroups)(request))
        }
      }
    }
  }

  def paidMemberRefiner(onFreeMember: RequestHeader => Result = changeTier(_)) =
    new ActionRefiner[AnyMemberTierRequest, PaidMemberRequest] {
      override def refine[A](request: AnyMemberTierRequest[A]) = Future.successful {
        request.member match {
          case paidMember: PaidMember => Right(MemberRequest(paidMember, request.request))
          case _ => Left(onFreeMember(request))
        }
      }
    }

  def googleAuthenticationRefiner(onNonAuthentication: RequestHeader => Result = OAuthActions.sendForAuth) = {
    new ActionRefiner[AuthRequest, IdAndGoogleAuthRequest] {
      override def refine[A](request: AuthRequest[A]) = Future.successful {
        //Copy the private helper method in play-googleauth to ensure the user is Google auth'd
        //see https://github.com/guardian/play-googleauth/blob/master/module/src/main/scala/com/gu/googleauth/actions.scala#L59-60
        val userIdentityOpt = UserIdentity.fromRequest(request).filter(_.isValid || !OAuthActions.authConfig.enforceValidity).map(googleUser => new IdAndGoogleAuthRequest(IdAndGoogle(request.user, googleUser), request))
        userIdentityOpt.toRight(onNonAuthentication(request))
      }
    }
  }

  def matchingGuardianEmail(onNonGuEmail: RequestHeader => Result = joinStaffMembership(_).flashing("error" -> "Identity email must match Guardian email")) = new ActionFilter[IdAndGoogleAuthRequest] {
    override def filter[A](request: IdAndGoogleAuthRequest[A]) =
      for {
        user <- IdentityService.getFullUserDetails(request.user.idMinimal, IdentityRequest(request))
      } yield {
        if (GuardianDomains.emailsMatch(request.user.google.email, user.primaryEmailAddress)) None
        else Some(onNonGuEmail(request))
      }
    }

  def metricRecord(cloudWatch: CloudWatch, metricName: String) = new ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) =
      Timing.record(cloudWatch, metricName) {
        block(request)
      }
  }
}
