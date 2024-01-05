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

import models.{CalculationRequest, CalculationSummaryData, Done}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.CalculationService
import uk.gov.hmrc.http.HeaderNames

import java.time.{LocalDate, ZoneOffset}
import scala.concurrent.Future

class CalculationControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures {

  "save" - {

    "must save a calculation request and return OK" in {

      val mockCalculationService = mock[CalculationService]

      when(mockCalculationService.save(any(), any())).thenReturn(Future.successful(Done))

      val application =
        new GuiceApplicationBuilder()
          .overrides(bind[CalculationService].toInstance(mockCalculationService))
          .build()

      running(application) {

        val calculationRequest = CalculationRequest(1, 2.2, 3.3, 4)

        val request =
          FakeRequest(POST, routes.CalculationController.save().url)
            .withJsonBody(Json.toJson(calculationRequest))
            .withHeaders(HeaderNames.xSessionId -> "foo")

        val result = route(application, request).value

        status(result) mustEqual OK
        verify(mockCalculationService, times(1)).save(eqTo("foo"), eqTo(calculationRequest))
      }
    }

    "must return BadRequest when there is no session id header" in {

      val mockCalculationService = mock[CalculationService]

      when(mockCalculationService.save(any(), any())).thenReturn(Future.successful(Done))

      val application =
        new GuiceApplicationBuilder()
          .overrides(bind[CalculationService].toInstance(mockCalculationService))
          .build()

      running(application) {

        val calculationRequest = CalculationRequest(1, 2.2, 3.3, 4)

        val request =
          FakeRequest(POST, routes.CalculationController.save().url)
            .withJsonBody(Json.toJson(calculationRequest))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        verify(mockCalculationService, never()).save(any(), any())
      }
    }
  }

  "summary" - {

    val mockCalculationService = mock[CalculationService]

    val application =
      new GuiceApplicationBuilder()
        .overrides(bind[CalculationService].toInstance(mockCalculationService))
        .build()

    val from = LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val to = LocalDate.of(2025, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    val summaryData = CalculationSummaryData(
      from = Some(from),
      to = Some(to),
      numberOfCalculations = 1000,
      numberOfUniqueSessions = 500,
      totalSavings = 10000,
      totalSavingsAveragedBySession = 50,
      averageSalary = 15000
    )

    "must return OK with CalculationSummaryData" in running(application) {

      when(mockCalculationService.summary(any(), any())).thenReturn(Future.successful(summaryData))

      val request =
        FakeRequest(GET, routes.CalculationController.summary(from = Some(from), to = Some(to)).url)

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockCalculationService).summary(from = eqTo(Some(from)), to = eqTo(Some(to)))
    }

    "must fail when CalculationService fails" in running(application) {

      when(mockCalculationService.summary(any(), any())).thenReturn(Future.failed(new RuntimeException()))

      val request =
        FakeRequest(GET, routes.CalculationController.summary(from = Some(from), to = Some(to)).url)

      route(application, request).value.failed.futureValue
    }
  }
}
