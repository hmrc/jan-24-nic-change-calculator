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

import cats.data.OptionT
import cats.implicits.toFunctorOps
import com.codahale.metrics.{MetricRegistry, Timer}
import models.Done
import play.api.{Configuration, Logging}
import repositories.CalculationRepository
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator

import java.time.{Clock, Duration}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class MetricOrchestrationService @Inject() (
                                             metricOrchestrator: MetricOrchestrator,
                                             calculationRepository: CalculationRepository,
                                             configuration: Configuration,
                                             clock: Clock,
                                             metricRegistry: MetricRegistry
                                           )(implicit ec: ExecutionContext) extends Logging {

  private val interval: FiniteDuration = configuration.get[FiniteDuration]("workers.metric-orchestrator-worker.interval")
  private val metricUpdateTimer: Timer = metricRegistry.timer("metric-update.timer")

  def updateMetrics(): Future[Done] =
    OptionT(calculationRepository.lastCalculation)
      .filter(_.timestamp isAfter clock.instant().minusSeconds(interval.toSeconds))
      .semiflatTap { _ =>
        logger.info("Attempting metric refresh")
        val start = clock.instant()
        val result = metricOrchestrator.attemptMetricRefresh()
        result.onComplete {
          case Success(_) =>
            logger.info("Metrics refreshed")
            metricUpdateTimer.update(Duration.between(start, clock.instant()))
          case Failure(_) =>
            logger.warn("Unable to refresh metrics")
        }
        result
      }.as(Done).getOrElse(Done)
}
