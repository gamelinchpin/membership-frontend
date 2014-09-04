package actions

import actions.Functions._
import configuration.Config
import controllers.{Cached, NoCache}
import play.api.http.HeaderNames._
import play.api.mvc.ActionBuilder
import play.api.mvc.Results._

trait CommonActions {
  val NoCacheAction = resultModifier(NoCache(_))

  val CachedAction = resultModifier(Cached(_))

  val Cors = resultModifier(_.withHeaders(
    ACCESS_CONTROL_ALLOW_ORIGIN -> Config.corsAllowOrigin,
    ACCESS_CONTROL_ALLOW_CREDENTIALS -> "true"))

  val AuthenticatedAction = NoCacheAction andThen authenticated()

  val AuthenticatedNonMemberAction = AuthenticatedAction andThen onlyNonMemberFilter()

  val MemberAction = NoCacheAction andThen authenticated() andThen memberRefiner()

  val PaidMemberAction = MemberAction andThen paidMemberRefiner()

  val AjaxAuthenticatedAction = Cors andThen NoCacheAction andThen authenticated(onUnauthenticated = _ => Forbidden)

  val AjaxAuthenticatedNonMemberAction = AuthenticatedAction andThen onlyNonMemberFilter(onPaidMember = _ => Forbidden)

  val AjaxMemberAction = AjaxAuthenticatedAction andThen memberRefiner(onNonMember = _ => Forbidden)

  val AjaxPaidMemberAction = AjaxMemberAction andThen paidMemberRefiner(onFreeMember = _ => Forbidden)
}