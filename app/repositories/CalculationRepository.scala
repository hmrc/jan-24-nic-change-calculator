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

package repositories

import models.{Calculation, Done}
import org.bson.conversions.Bson
import org.mongodb.scala.model.Accumulators.sum
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.model.Aggregates._
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CalculationRepository @Inject()(mongoComponent: MongoComponent)
                                     (implicit ec: ExecutionContext)
extends PlayMongoRepository[Calculation](
  collectionName = "calculations",
  mongoComponent = mongoComponent,
  domainFormat   = Calculation.format,
  extraCodecs    = Seq(Codecs.playFormatCodec(DistinctSessionIds.format)),
  indexes        = Seq(
    IndexModel(
      Indexes.ascending("timestamp"),
      IndexOptions()
        .name("timestampIdx")
        .unique(false)
    )
  )
) {

  override lazy val requiresTtlIndex: Boolean = false

  def save(calculation: Calculation): Future[Done] =
    collection.insertOne(calculation)
      .toFuture()
      .map(_ => Done)

  def numberOfCalculations: Future[Long] =
    collection.countDocuments().head()

  def numberOfUniqueSessions: Future[Long] =
    collection.aggregate[DistinctSessionIds](Seq(
      group("$sessionId", sum("count", 1)),
      count("distinctSessionIds"))
    ).headOption().map(_.map(_.distinctSessionIds).getOrElse(0))
}

final case class DistinctSessionIds(distinctSessionIds: Long)

object DistinctSessionIds {
  implicit lazy val format: Format[DistinctSessionIds] = Json.format
}
