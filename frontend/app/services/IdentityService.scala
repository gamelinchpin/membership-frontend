package services

import actions.IdAndGoogle
import com.gu.membership.util.Timing
import com.gu.membership.zuora.Address
import configuration.Config
import controllers.IdentityRequest
import forms.MemberForm._
import model.UserDeserializer._
import model.{IdMinimalUser, IdUser}
import monitoring.IdentityApiMetrics
import play.api.Logger
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IdentityServiceError(s: String) extends Throwable {
  override def getMessage: String = s
}

trait IdentityService {

  def getFullUserDetails(user: IdMinimalUser, identityRequest: IdentityRequest): Future[IdUser] =
    IdentityApi.get(s"user/${user.id}", identityRequest.headers, identityRequest.trackingParameters)
      .map(_.getOrElse(throw IdentityServiceError(s"Couldn't find user with ID ${user.id}")))

  def doesUserPasswordExist(identityRequest: IdentityRequest): Future[Boolean] =
    IdentityApi.getUserPasswordExists(identityRequest.headers, identityRequest.trackingParameters)

  def updateUserFieldsBasedOnJoining(user: IdMinimalUser, formData: JoinForm, identityRequest: IdentityRequest) {

    val billingDetails = formData match {
      case billingForm: PaidMemberJoinForm =>
        val billingAddressForm = billingForm.billingAddress.getOrElse(billingForm.deliveryAddress)
        billingAddress(billingAddressForm)
      case _ => Json.obj()
    }

    val fields = Json.obj(
      "secondName" -> formData.name.last,
      "firstName" -> formData.name.first
    ) ++ deliveryAddress(formData.deliveryAddress) ++ billingDetails

    val json = Json.obj("privateFields" -> fields)
    postFields(json, user, identityRequest)
  }

  def updateUserPassword(password: String, identityRequest: IdentityRequest, userId: String) {
    val json = Json.obj("newPassword" -> password)
    IdentityApi.post("/user/password", json, identityRequest.headers, identityRequest.trackingParameters, "update-user-password")
  }

  def updateUserFieldsBasedOnUpgrade(user: IdMinimalUser, formData: PaidMemberChangeForm, identityRequest: IdentityRequest) {
    val billingAddressForm = formData.billingAddress.getOrElse(formData.deliveryAddress)
    val fields = deliveryAddress(formData.deliveryAddress) ++ billingAddress(billingAddressForm)
    val json = Json.obj("privateFields" -> fields)
    postFields(fields, user, identityRequest)
  }

  def updateIdentityEmailToMatchGoogle(user: IdAndGoogle, identityRequest: IdentityRequest) = {
    val json = Json.obj("primaryEmailAddress" -> user.google.email)
    postFields(json, user.idMinimal, identityRequest)
  }

  private def postFields(json: JsObject, user: IdMinimalUser, identityRequest: IdentityRequest) = {
    Logger.info(s"Posting updated information to Identity for user :${user.id}")
    IdentityApi.post(s"user/${user.id}", json, identityRequest.headers, identityRequest.trackingParameters, "update-user")
  }

  private def deliveryAddress(addressForm: Address): JsObject = {
    Json.obj(
      "address1" -> addressForm.lineOne,
      "address2" -> addressForm.lineTwo,
      "address3" -> addressForm.town,
      "address4" -> addressForm.countyOrState,
      "postcode" -> addressForm.postCode,
      "country" -> addressForm.country.name
    )
  }

  private def billingAddress(billingAddress: Address): JsObject = {
    Json.obj(
      "billingAddress1" -> billingAddress.lineOne,
      "billingAddress2" -> billingAddress.lineTwo,
      "billingAddress3" -> billingAddress.town,
      "billingAddress4" -> billingAddress.countyOrState,
      "billingPostcode" -> billingAddress.postCode,
      "billingCountry" -> billingAddress.country.name
    )
  }
}

object IdentityService extends IdentityService

object IdentityApi {

  def getUserPasswordExists(headers:List[(String, String)], parameters: List[(String, String)]) : Future[Boolean] = {
    val endpoint = "user/password-exists"
    val url = s"${Config.idApiUrl}/$endpoint"
    Timing.record(IdentityApiMetrics, "get-user-password-exists") {
      WS.url(url).withHeaders(headers: _*).withQueryString(parameters: _*).withRequestTimeout(1000).get().map { response =>
        recordAndLogResponse(response.status, "GET user-password-exists", endpoint)
        (response.json \ "passwordExists").asOpt[Boolean].getOrElse(throw new IdentityApiError(s"$url did not return a boolean"))
      }
    }
  }

  def get(endpoint: String, headers:List[(String, String)], parameters: List[(String, String)]) : Future[Option[IdUser]] = {
    Timing.record(IdentityApiMetrics, "get-user") {
      WS.url(s"${Config.idApiUrl}/$endpoint").withHeaders(headers: _*).withQueryString(parameters: _*).withRequestTimeout(1000).get().map { response =>
        recordAndLogResponse(response.status, "GET user", endpoint)
        (response.json \ "user").asOpt[IdUser]
      }.recover {
        case _ => None
      }
    }
  }

  def post(endpoint: String, data: JsObject, headers: List[(String, String)], parameters: List[(String, String)], metricName: String): Future[Int] = {
    Timing.record(IdentityApiMetrics, metricName) {
      val response = WS.url(s"${Config.idApiUrl}/$endpoint").withHeaders(headers: _*).withQueryString(parameters: _*).withRequestTimeout(5000).post(data)
      response.map (r => recordAndLogResponse(r.status, s"POST $metricName", endpoint ))
      response.map(_.status)
    }
  }

  private def recordAndLogResponse(status: Int, responseMethod: String, endpoint: String) {
    Logger.info(s"$responseMethod response ${status} for endpoint ${endpoint}")
    IdentityApiMetrics.putResponseCode(status, responseMethod)
  }
}

case class IdentityApiError(s: String) extends Throwable {
  override def getMessage: String = s
}
