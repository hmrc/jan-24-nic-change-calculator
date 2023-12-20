/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import models.CalculationRequest
import play.api.mvc.{Action, ControllerComponents}
import services.CalculationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CalculationController @Inject()(cc: ControllerComponents, calculationService: CalculationService)(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def save(): Action[CalculationRequest] = Action(parse.tolerantJson[CalculationRequest]).async {
    implicit request =>

      val hc = HeaderCarrierConverter.fromRequest(request)
      val maybeSessionId = hc.sessionId

      maybeSessionId.map { sessionId =>
        calculationService.save(sessionId.value, request.body).map(_ => Ok)
      }.getOrElse {
        Future.successful(BadRequest)
      }
  }
}
