/*
 * Copyright 2024 HM Revenue & Customs
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

package services

import com.codahale.metrics.MetricRegistry
import models.Calculation
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import repositories.CalculationRepository
import uk.gov.hmrc.crypto.Scrambled
import uk.gov.hmrc.mongo.metrix.{MetricOrchestrationResult, MetricOrchestrator}

import java.time.{Clock, LocalDate, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MetricOrchestrationServiceSpec extends AnyFreeSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  private val metricOrchestrator = mock[MetricOrchestrator]
  private val calculationRepository = mock[CalculationRepository]
  private val configuration = Configuration("workers.metric-orchestrator-worker.interval" -> "10seconds")
  private val clock = Clock.fixed(LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
  private val metricRegistry = new MetricRegistry();

  private val metricOrchestrationService =
    new MetricOrchestrationService(metricOrchestrator, calculationRepository, configuration, clock, metricRegistry)

  private val calculation = Calculation(
    sessionId = Scrambled("foo"),
    annualSalary = 1.1,
    year1EstimatedNic = 2.2,
    year2EstimatedNic = 3.3,
    roundedSaving = 4,
    timestamp = clock.instant()
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset[Any](metricOrchestrator, calculationRepository)
  }

  ".updateMetrics" - {

    "must update metrics when the last calculation was added recently" in {

      when(calculationRepository.lastCalculation)
        .thenReturn(Future.successful(Some(calculation)))

      when(metricOrchestrator.attemptMetricRefresh(any(), any())(any()))
        .thenReturn(Future.successful(MetricOrchestrationResult.UpdatedAndRefreshed(Map.empty, Seq.empty)))

      metricOrchestrationService.updateMetrics().futureValue

      verify(metricOrchestrator).attemptMetricRefresh(any(), any())(any())
    }

    "must not update metrics when the last calculation was added too far in the past" in {

      when(calculationRepository.lastCalculation)
        .thenReturn(Future.successful(Some(calculation.copy(timestamp = clock.instant().minusSeconds(11)))))

      when(metricOrchestrator.attemptMetricRefresh(any(), any())(any()))
        .thenReturn(Future.successful(MetricOrchestrationResult.UpdatedAndRefreshed(Map.empty, Seq.empty)))

      metricOrchestrationService.updateMetrics().futureValue

      verify(metricOrchestrator, never()).attemptMetricRefresh(any(), any())(any())
    }

    "must not update metrics when there are no calculations" in {

      when(calculationRepository.lastCalculation)
        .thenReturn(Future.successful(None))

      when(metricOrchestrator.attemptMetricRefresh(any(), any())(any()))
        .thenReturn(Future.successful(MetricOrchestrationResult.UpdatedAndRefreshed(Map.empty, Seq.empty)))

      metricOrchestrationService.updateMetrics().futureValue

      verify(metricOrchestrator, never()).attemptMetricRefresh(any(), any())(any())
    }
  }
}
