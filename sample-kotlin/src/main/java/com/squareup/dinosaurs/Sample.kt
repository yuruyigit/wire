/*
 * Copyright 2015 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.dinosaurs

import com.squareup.geology.Period
import java.io.IOException
import java.util.Arrays
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.ByteString.Companion.decodeBase64

class Sample {
  @Throws(IOException::class)
  fun run() {
    // Create an immutable value object with the Builder API.
    val stegosaurus = Dinosaur.Builder()
        .name("Stegosaurus")
        .period(Period.JURASSIC)
        .length_meters(9.0)
        .mass_kilograms(5_000.0)
        .picture_urls(Arrays.asList("http://goo.gl/LD5KY5", "http://goo.gl/VYRM67"))
        .build()

    // Encode that value to bytes, and print that as base64.
    val stegosaurusEncoded = Dinosaur.ADAPTER.encode(stegosaurus)
    System.out.println(stegosaurusEncoded.toByteString().base64())

    // Decode base64 bytes, and decode those bytes as a dinosaur.
    val tyrannosaurusEncoded =
      ("Cg1UeXJhbm5vc2F1cnVzEmhodHRwOi8vdmln" +
          "bmV0dGUxLndpa2lhLm5vY29va2llLm5ldC9qdXJhc3NpY3BhcmsvaW1hZ2VzLzYvNmEvTGVnbzUuanBnL3Jldmlz" +
          "aW9uL2xhdGVzdD9jYj0yMDE1MDMxOTAxMTIyMRJtaHR0cDovL3ZpZ25ldHRlMy53aWtpYS5ub2Nvb2tpZS5uZXQv" +
          "anVyYXNzaWNwYXJrL2ltYWdlcy81LzUwL1JleHlfcHJlcGFyaW5nX2Zvcl9iYXR0bGVfd2l0aF9JbmRvbWludXNf" +
          "cmV4LmpwZxmamZmZmZkoQCEAAAAAAJC6QCgB")
          .decodeBase64()
    val tyrannosaurus = Dinosaur.ADAPTER.decode(tyrannosaurusEncoded!!.toByteArray())

    // Print both of our dinosaurs.
    println(stegosaurus.name + " is " + stegosaurus.length_meters + " meters long!")
    println(tyrannosaurus.name + " weighs " + tyrannosaurus.mass_kilograms + " kilos!")
  }

  companion object {
    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
      Sample().run()
    }
  }
}
