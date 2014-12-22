package controllers

import scala.concurrent.Future

import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json

import com.gu.membership.salesforce._
import com.gu.membership.salesforce.Tier.Tier

import actions.MemberRequest
import forms.MemberForm._
import model.StripeSerializer._
import model._
import services._
import actions._

trait DowngradeTier {
  self: TierController =>

  def downgradeToFriend() = PaidMemberAction { implicit request =>
    Ok(views.html.tier.downgrade.confirm(request.member.tier))
  }

  def downgradeToFriendConfirm() = PaidMemberAction.async { implicit request => // POST
    for {
      cancelledSubscription <- request.touchpointBackend.downgradeSubscription(request.member, FriendTierPlan)
    } yield Redirect("/tier/change/friend/summary")
  }

  def downgradeToFriendSummary() = PaidMemberAction.async { implicit request =>
    val subscriptionService = request.touchpointBackend.subscriptionService
    for {
      subscriptionStatus <- subscriptionService.getSubscriptionStatus(request.member.salesforceAccountId)
      currentSubscription <- subscriptionService.getSubscriptionDetails(subscriptionStatus.current)
      futureSubscription <- subscriptionService.getSubscriptionDetails(subscriptionStatus.future.get)
    } yield Ok(views.html.tier.downgrade.summary(currentSubscription, futureSubscription))
  }
}

trait UpgradeTier {
  self: TierController =>

  def upgrade(tier: Tier) = MemberAction.async { implicit request =>
    if (request.member.tier < tier) {
      for {
        user <- IdentityService(IdentityApi).getFullUserDetails(request.user, IdentityRequest(request))
      } yield {
        val pageInfo = PageInfo.default.copy(stripePublicKey = Some(request.touchpointBackend.stripeService.publicKey))
        Ok(views.html.tier.upgrade.upgradeForm(request.member.tier, tier, user.privateFields, pageInfo))
      }
    }
    else
      Future.successful(NotFound)
  }

  def upgradeConfirm(tier: Tier) = MemberAction.async { implicit request =>
    request.member match {
      case freeMember: FreeMember =>
        paidMemberChangeForm.bindFromRequest.fold(_ => Future.successful(BadRequest), doUpgrade(freeMember, tier))
      case _ => Future.successful(NotFound)
    }
  }

  private def doUpgrade(freeMember: FreeMember, tier: Tier)(formData: PaidMemberChangeForm)(implicit request: MemberRequest[_, _]) = {
    MemberService.upgradeSubscription(freeMember, request.user, tier, formData, IdentityRequest(request))
      .map { _ => Ok(Json.obj("redirect" -> routes.TierController.upgradeThankyou(tier).url)) }
      .recover {
        case error: Stripe.Error => Forbidden(Json.toJson(error))
        case error: Zuora.ResultError => Forbidden
        case error: ScalaforceError => Forbidden
      }
  }

  def upgradeThankyou(tier: Tier) = Joiner.thankyou(tier, upgrade=true)
}

trait CancelTier {
  self: TierController =>

  def cancelTier() = MemberAction { implicit request =>
    Ok(views.html.tier.cancel.confirm(request.member.tier))
  }

  def cancelTierConfirm() = MemberAction.async { implicit request =>
    for {
      _ <- request.touchpointBackend.cancelSubscription(request.member)
    } yield {
      Redirect("/tier/cancel/summary")
    }
  }

  def cancelTierSummary() = AuthenticatedAction.async { implicit request =>
    def subscriptionDetailsFor(memberOpt: Option[Member]) = {
      memberOpt.collect { case paidMember: PaidMember =>
        request.touchpointBackend.subscriptionService.getCurrentSubscriptionDetails(paidMember.salesforceAccountId)
      }
    }

    for {
      memberOpt <- request.touchpointBackend.memberRepository.get(request.user.id)
      subscriptionDetails <- Future.sequence(subscriptionDetailsFor(memberOpt).toSeq)
    } yield Ok(views.html.tier.cancel.summary(subscriptionDetails.headOption))
  }
}

trait TierController extends Controller with UpgradeTier with DowngradeTier with CancelTier {

  def change() = MemberAction { implicit request =>
    Ok(views.html.tier.change(request.member.tier))
  }
}

object TierController extends TierController
