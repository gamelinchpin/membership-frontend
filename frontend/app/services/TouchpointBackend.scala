package services

import com.gu.membership.salesforce.Member.Keys
import com.gu.membership.salesforce._

import configuration.Config
import model.Stripe.Card
import model.{IdMinimalUser, FriendTierPlan, TierPlan}
import services.zuora.ZuoraService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TouchpointBackend {

  def apply(touchpointBackendConfig: TouchpointBackendConfig): TouchpointBackend = {
    val stripeService = new StripeService(touchpointBackendConfig.stripe)

    val zuoraService = new ZuoraService(touchpointBackendConfig.zuora)

    val memberRepository = new FrontendMemberRepository(touchpointBackendConfig.salesforce)

    TouchpointBackend(memberRepository, stripeService, zuoraService)
  }

  val Normal = TouchpointBackend(Config.touchpointDefaultBackend)
  val TestUser = TouchpointBackend(Config.touchpointTestBackend)

  val All = Seq(Normal, TestUser)

  def forUser(user: IdMinimalUser) = if (user.isTestUser) TestUser else Normal
}

case class TouchpointBackend(
  memberRepository: FrontendMemberRepository,
  stripeService: StripeService,
  zuoraService : ZuoraService) {

  def start() = {
    memberRepository.start()
    zuoraService.start()
  }

  val subscriptionService = new SubscriptionService(zuoraService.apiConfig.productRatePlans, zuoraService)

  def updateDefaultCard(member: PaidMember, token: String): Future[Card] = {
    for {
      customer <- stripeService.Customer.updateCard(member.stripeCustomerId, token)
      memberId <- memberRepository.upsert(member.identityId, Map(Keys.DEFAULT_CARD_ID -> customer.card.id))
    } yield customer.card
  }

  def cancelSubscription(member: Member): Future[String] = {
    for {
      subscription <- subscriptionService.cancelSubscription(member.salesforceAccountId, member.tier == Tier.Friend)
    } yield {
      memberRepository.metrics.putCancel(member.tier)
      ""
    }
  }

  def downgradeSubscription(member: Member, tierPlan: TierPlan): Future[String] = {
    for {
      _ <- subscriptionService.downgradeSubscription(member.salesforceAccountId, FriendTierPlan)
    } yield {
      memberRepository.metrics.putDowngrade(tierPlan.tier)
      ""
    }
  }

}
