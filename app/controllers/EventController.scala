package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import services.EventbriteService

trait EventController extends Controller {

  val eventService: EventbriteService

  def renderEventPage(id: String) = Action.async {
    eventService.getEvent(id).map(event => Ok(views.html.events.eventPage(event)))
  }

  def renderEventsIndex = Action.async {
    eventService.getAllEvents.map(events => Ok(views.html.events.eventsIndex(events)))
  }

}

object EventController extends EventController {
  override val eventService: EventbriteService = EventbriteService
}