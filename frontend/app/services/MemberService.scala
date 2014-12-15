package services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import com.typesafe.scalalogging.slf4j.LazyLogging

import com.gu.membership.salesforce.Member.Keys
import com.gu.membership.salesforce._
import com.gu.membership.util.Timing

import configuration.Config
import controllers.IdentityRequest
import forms.MemberForm._
import model.{IdMinimalUser, IdUser, ProductRatePlan, PaidTierPlan}
import model.Eventbrite.{MasterclassEvent, GuLiveEvent, EBCode, RichEvent}
import model.Stripe.Customer
import monitoring.MemberMetrics
import utils.ScheduledTask

case class MemberServiceError(s: String) extends Throwable {
  override def getMessage: String = s
}

class FrontendMemberRepository(salesforceConfig: SalesforceConfig) extends MemberRepository with ScheduledTask[Authentication] {
  val metrics = new MemberMetrics(salesforceConfig.envName)

  val initialValue = Authentication("", "")
  val initialDelay = 0.seconds
  val interval = 30.minutes

  def refresh() = salesforce.getAuthentication

  val salesforce = new Scalaforce {
    val consumerKey = salesforceConfig.consumerKey
    val consumerSecret = salesforceConfig.consumerSecret

    val apiURL = salesforceConfig.apiURL.toString
    val apiUsername = salesforceConfig.apiUsername
    val apiPassword = salesforceConfig.apiPassword
    val apiToken = salesforceConfig.apiToken

    val stage = Config.stage
    val application = "Frontend"

    def authentication: Authentication = agent.get()
  }
}

trait MemberService extends LazyLogging {
  def initialData(user: IdUser, formData: JoinForm) = Map(
    Keys.EMAIL -> user.primaryEmailAddress,
    Keys.FIRST_NAME -> formData.name.first,
    Keys.LAST_NAME -> formData.name.last,
    Keys.MAILING_STREET -> formData.deliveryAddress.line,
    Keys.MAILING_CITY -> formData.deliveryAddress.town,
    Keys.MAILING_STATE -> formData.deliveryAddress.countyOrState,
    Keys.MAILING_POSTCODE -> formData.deliveryAddress.postCode,
    Keys.MAILING_COUNTRY -> formData.deliveryAddress.country.alpha2,
    Keys.ALLOW_MEMBERSHIP_MAIL -> true
  ) ++
    formData.marketingChoices.thirdParty.map( Keys.ALLOW_THIRD_PARTY_EMAIL -> _) ++
    formData.marketingChoices.gnm.map( Keys.ALLOW_GU_RELATED_MAIL -> _)

  def memberData(plan: ProductRatePlan, customerOpt: Option[Customer]) = Map(
    Keys.TIER -> plan.salesforceTier
  ) ++ customerOpt.map { customer =>
    Map(
      Keys.CUSTOMER_ID -> customer.id,
      Keys.DEFAULT_CARD_ID -> customer.card.id
    )
  }.getOrElse(Map.empty)

  def createMember(user: IdMinimalUser, formData: JoinForm, identityRequest: IdentityRequest): Future[String] = {
    val touchpointBackend = TouchpointBackend.forUser(user)
    val identityService = IdentityService(IdentityApi)

    Timing.record(touchpointBackend.memberRepository.metrics, "createMember") {
      def futureCustomerOpt = formData match {
        case paid: PaidMemberJoinForm => touchpointBackend.stripeService.Customer.create(user.id, paid.payment.token).map(Some(_))
        case _ => Future.successful(None)
      }

      formData.password.map(identityService.updateUserPassword(_, identityRequest, user.id))

      for {
        fullUser <- identityService.getFullUserDetails(user, identityRequest)
        customerOpt <- futureCustomerOpt
        memberId <- touchpointBackend.memberRepository.upsert(user.id, initialData(fullUser, formData))
        subscription <- touchpointBackend.subscriptionService.createSubscription(memberId, formData, customerOpt)

        // Set some fields once subscription has been successful
        updatedMember <- touchpointBackend.memberRepository.upsert(user.id, memberData(formData.plan, customerOpt))
      } yield {
        identityService.updateUserFieldsBasedOnJoining(user, formData, identityRequest)

        touchpointBackend.memberRepository.metrics.putSignUp(formData.plan)
        memberId.account
      }
    }.andThen {
      case Success(memberAccount) => logger.debug(s"createMember() success user=${user.id} memberAccount=$memberAccount")
      case Failure(error) => {
        logger.error(s"Error in createMember() user=${user.id}", error)
        touchpointBackend.memberRepository.metrics.putFailSignUp(formData.plan)
      }
    }
  }

  def createDiscountForMember(member: Member, event: RichEvent): Future[Option[EBCode]] = {
    val eventService = event match {
      case _: GuLiveEvent => GuardianLiveEventService
      case _: MasterclassEvent => MasterclassEventService
    }

    member.tier match {
      case Tier.Friend => Future.successful(None)

      case _ =>
        if (event.memberTickets.nonEmpty) {
          // Add a "salt" to make access codes different to discount codes
          val code = DiscountCode.generate(s"A_${member.identityId}_${event.id}")
          eventService.createOrGetAccessCode(event, code, event.memberTickets).map(Some(_))
        } else {
          val code = DiscountCode.generate(s"${member.identityId}_${event.id}")
          eventService.createOrGetDiscount(event, code).map(Some(_))
        }
    }
  }

  // TODO: this currently only handles free -> paid
  def upgradeSubscription(member: FreeMember, user: IdMinimalUser, newTier: Tier.Tier, form: PaidMemberChangeForm, identityRequest: IdentityRequest): Future[String] = {
    val touchpointBackend = TouchpointBackend.forUser(user)
    val newPaidPlan = PaidTierPlan(newTier, form.payment.annual)
    for {
      customer <- touchpointBackend.stripeService.Customer.create(user.id, form.payment.token)
      paymentResult <- touchpointBackend.subscriptionService.createPaymentMethod(member.salesforceAccountId, customer)
      subscriptionResult <- touchpointBackend.subscriptionService.upgradeSubscription(member.salesforceAccountId, newPaidPlan)
      memberId <- touchpointBackend.memberRepository.upsert(member.identityId, memberData(newPaidPlan, Some(customer)))
    } yield {
      IdentityService(IdentityApi).updateUserFieldsBasedOnUpgrade(user, form, identityRequest)
      touchpointBackend.memberRepository.metrics.putUpgrade(newTier)
      memberId.account
    }
  }
}

object MemberService extends MemberService
