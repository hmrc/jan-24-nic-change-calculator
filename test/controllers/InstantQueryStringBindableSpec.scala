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

package controllers

import org.scalacheck.Gen
import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.mvc.QueryStringBindable

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}

class InstantQueryStringBindableSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks with OptionValues with EitherValues {

  private def datesBetween(min: Instant, max: Instant): Gen[Instant] =
    Gen.choose(min.toEpochMilli, max.toEpochMilli).map {
      millis =>
        Instant.ofEpochMilli(millis)
    }

  private val bindable = implicitly[QueryStringBindable[Instant]]

  private val dateFormat = DateTimeFormatter.ISO_INSTANT

  "must bind and unbind instants" in {

    val from = LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val to = LocalDate.of(2025, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    forAll(datesBetween(from, to), Gen.alphaNumStr) { (instant, key) =>
      val string = dateFormat.format(instant)
      val data = Map(key -> Seq(string))
      val result = bindable.bind(key, data).value.value
      bindable.unbind(key, result) mustEqual s"$key=$string"
    }
  }

  "must return an error when an instant cannot be bound" in {
    forAll(Gen.alphaNumStr, Gen.alphaNumStr) { (key, value) =>
      bindable.bind(key, Map(key -> Seq(value))).value.left.value mustEqual s"$key: Error parsing instant"
    }
  }

  "must return `None` when there is no data for the key" in {
    forAll(Gen.alphaNumStr) { key =>
      bindable.bind(key, Map.empty) mustBe empty
      bindable.bind(key, Map(key -> Seq.empty)) mustBe empty
    }
  }
}
