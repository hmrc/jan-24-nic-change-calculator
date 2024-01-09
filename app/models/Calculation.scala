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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.crypto.Scrambled
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class Calculation(
                              sessionId: Scrambled,
                              annualSalary: BigDecimal,
                              year1EstimatedNic: BigDecimal,
                              year2EstimatedNic: BigDecimal,
                              roundedSaving: BigDecimal,
                              saving: Option[BigDecimal],
                              timestamp: Instant
                            )

object Calculation extends MongoJavatimeFormats.Implicits {

  private lazy val reads: Reads[Calculation] = (
    (__ \ "sessionId").read[String].map(Scrambled) and
    (__ \ "annualSalary").read[BigDecimal] and
    (__ \ "year1EstimatedNic").read[BigDecimal] and
    (__ \ "year2EstimatedNic").read[BigDecimal] and
    (__ \ "roundedSaving").read[BigDecimal] and
    (__ \ "saving").readNullable[BigDecimal] and
    (__ \ "timestamp").read[Instant]
  )(Calculation.apply _)

  private lazy val writes: OWrites[Calculation] = (
    (__ \ "sessionId").write[String] and
    (__ \ "annualSalary").write[BigDecimal] and
    (__ \ "year1EstimatedNic").write[BigDecimal] and
    (__ \ "year2EstimatedNic").write[BigDecimal] and
    (__ \ "roundedSaving").write[BigDecimal] and
    (__ \ "saving").writeNullable[BigDecimal] and
    (__ \ "timestamp").write[Instant]
  )(c => (c.sessionId.value, c.annualSalary, c.year1EstimatedNic, c.year2EstimatedNic, c.roundedSaving, c.saving, c.timestamp))

  implicit lazy val format: OFormat[Calculation] = OFormat(reads, writes)
}
