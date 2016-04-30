/*
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
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package testzotohlab.scala.frwk.util

import org.scalatest.Assertions._
import org.scalatest._


import org.apache.shiro.config._
import org.apache.shiro.realm._
import org.apache.shiro.authc.credential._

import org.apache.shiro.authc._
import org.apache.shiro.subject._
import org.apache.shiro.SecurityUtils
import org.apache.shiro.subject.Subject



class Test  extends FunSuite with BeforeAndAfterEach with BeforeAndAfterAll {

  def beforeAll(configMap: Map[String, Any]) {
  }

  def afterAll(configMap: Map[String, Any]) {
  }

  override def beforeEach() { }

  override def afterEach() { }

  test("testDummy") {
    assert(true)
  }

}
