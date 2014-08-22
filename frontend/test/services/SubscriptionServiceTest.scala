package services

import org.specs2.mutable.Specification
import model.Zuora.SubscriptionDetails
import org.joda.time.DateTime

class SubscriptionServiceTest extends Specification {
  "SubscriptionService" should {
    "extract an invoice from a map" in {
      val startDate = new DateTime(2014, 10, 6, 10, 0)
      val endDate = new DateTime(2014, 11, 7, 10, 0)

      val subscriptionDetails = SubscriptionDetails(
        Map(
          "Id" -> "Id",
          "Name" -> "Product name"
        ),
        Map(
          "EffectiveStartDate" -> "2014-10-06T10:00:00",
          "ChargedThroughDate" -> "2014-11-07T10:00:00",
          "Price" -> "12"
        )
      )

      subscriptionDetails mustEqual SubscriptionDetails("Product name", 12.0f, startDate, endDate, "Id")
      subscriptionDetails.annual mustEqual false
    }
  }
}