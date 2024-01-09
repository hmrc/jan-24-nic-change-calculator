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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.crypto.Scrambled
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

class CalculationSpec extends AnyFreeSpec with Matchers {

  "Calculation" - {

    "must serialise and deserialise to/from json" - {

      import MongoJavatimeFormats.Implicits._

      val timestamp = Instant.ofEpochSecond(1)

      "when saving is present" in {

        val calculation = Calculation(
          sessionId = Scrambled("foo"),
          annualSalary = 1.1,
          year1EstimatedNic = 2.2,
          year2EstimatedNic = 3.3,
          roundedSaving = 4,
          saving = Some(4.5),
          timestamp = timestamp
        )

        val json = Json.toJson(calculation)

        json mustEqual Json.obj(
          "sessionId" -> "foo",
          "annualSalary" -> 1.1,
          "year1EstimatedNic" -> 2.2,
          "year2EstimatedNic" -> 3.3,
          "roundedSaving" -> 4,
          "saving" -> 4.5,
          "timestamp" -> timestamp
        )

        val result = json.validate[Calculation]

        result mustEqual JsSuccess(calculation)
      }

      "when saving is absent" in {

        val calculation = Calculation(
          sessionId = Scrambled("foo"),
          annualSalary = 1.1,
          year1EstimatedNic = 2.2,
          year2EstimatedNic = 3.3,
          roundedSaving = 4,
          saving = None,
          timestamp = timestamp
        )

        val json = Json.toJson(calculation)

        json mustEqual Json.obj(
          "sessionId" -> "foo",
          "annualSalary" -> 1.1,
          "year1EstimatedNic" -> 2.2,
          "year2EstimatedNic" -> 3.3,
          "roundedSaving" -> 4,
          "timestamp" -> timestamp
        )

        val result = json.validate[Calculation]

        result mustEqual JsSuccess(calculation)
      }
    }
  }
}
