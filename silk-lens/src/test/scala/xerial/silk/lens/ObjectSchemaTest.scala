/*
 * Copyright 2012 Taro L. Saito
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xerial.silk.lens

import java.lang.reflect.ParameterizedType
import tools.scalap.scalax.rules.scalasig.{TypeRefType, MethodSymbol}
import xerial.silk.util.{option, SilkSpec}


//--------------------------------------
//
// ObjectSchemaTest.scala
// Since: 2012/01/17 10:17
//
//--------------------------------------

object ObjectSchemaTest {

  class A(val id: Int, val name: String)

  class C(val flag: Option[Int])

}

class B(val id: Int, val flag: Option[Boolean])

/**
 * @author leo
 */
class ObjectSchemaTest extends SilkSpec {

  import ObjectSchemaTest._

  "ObjectSchema" should {
    "enumerate all fields" in {

      val s = new ObjectSchema(classOf[A])
      debug {
        s.toString
      }
      s.name must be(classOf[A].getSimpleName)
      s.fullName must be(classOf[A].getName)

      val attr = s.parameters
      attr.length must be(2)
      attr(0).name must be("id")

      attr(0).rawType must be(classOf[Int])
      attr(1).name must be("name")
      attr(1).rawType must be(classOf[String])
    }


    trait ClassFixture {
      val cg = classOf[GlocalCls]
      val co = classOf[ScalaClassLensTest.ClsInObj]
      val params = Array(
        ("id", classOf[Int]),
        ("flag", classOf[Option[Int]]),
        ("list", classOf[Array[String]]),
        ("map", classOf[Map[String, Float]]))
    }

    import ObjectSchema.toSchema

    "find class definition containing ScalaSignature" in {
      new ClassFixture {
        val s1 = cg.findSignature
        val s2 = co.findSignature

        s1 should be('defined)
        s2 should be('defined)
      }
    }

    "find constructor parameters defined in global classes" in {
      new ClassFixture {
        val cc = cg.constructor

        //debug { p.mkString(", ") }
        val p = cc.params
        p.size must be(4)

        for (((name, t), i) <- params.zipWithIndex) {
          p(i).name must be(name)
          when("type is " + t)
          p(i).rawType.isAssignableFrom(t)
        }
      }
    }

    "find constructor parameters defined in object classes" in {
      new ClassFixture {
        val cc = co.constructor
        //debug { p.mkString(", ") }
        val p = cc.params
        p.size must be(4)

        for (((name, t), i) <- params.zipWithIndex) {
          p(i).name must be(name)
          when("type is " + t)
          p(i).rawType.isAssignableFrom(t)
        }
      }
    }

    "find root constructor" in {
      val c1 = classOf[ScalaClassLensTest.ClsInObj].constructor
      debug {
        c1
      }
      val c2 = classOf[GlocalCls].constructor
      debug {
        c2
      }
    }


    "find attributes defined in class body" in {
      val c = classOf[ValInBody].parameters
      debug {
        "ValInBody: " + c.mkString(", ")
      }
      c.size should be(3)
    }

    "find methods" in {
      val c = classOf[MethodHolder].methods
      debug {
        c.mkString(", ")
      }

      c.size must be(3)

    }

    "find annotations attached to method arguments" in {
      val methods = classOf[CommandLineAPI].methods
      val m = methods(0)
      m.name must be ("hello")
      val name = m.findAnnotationOf[option](0).get
      name.symbol must be("n")
      name.description must be ("name")
      val dh = m.findAnnotationOf[option](1).get
      dh.symbol must be("h")
      dh.description must be ("display help message")
    }

    "find annotations of class parameters" in {
      val params = classOf[CommandLineOption].parameters
      val o1 = params(0)
      val a1 = o1.findAnnotationOf[option].get
      a1.symbol must be ("h")
      val o2 = params(1)
      val a2 = o2.findAnnotationOf[option].get
      a2.symbol must be ("f")
    }

  }

}

class GlocalCls(val id: Int, val flag: Option[Int], val list: Array[String], val map: Map[String, Float])

class ValInBody(val id: Int = 1) {
  val args: Seq[String] = Seq.empty
  val opt: Option[Int] = None
}

class MethodHolder {
  def hello: String = "hello"

  def hello(name: String): String = "hello " + name

  def methodWithOption(displayHelp: Option[Boolean]): Unit = {}
}

object ScalaClassLensTest {

  class ClsInObj(val id: Int, val flag: Option[Int], val list: Array[String], val map: Map[String, Float])

  class Dummy(val name: String) {
    def dummyMethod: Unit = {}
  }

}


class CommandLineAPI {
  def hello(
             @option(symbol = "n", description = "name")
             name: String,
             @option(symbol = "h", description = "display help message")
             displayHelp: Option[Boolean]
             ): String = {
    "hello"
  }
}

class CommandLineOption
(
  @option(symbol="h", description="display help")
  val displayHelp : Option[Boolean],
  @option(symbol="f", description="input files")
  val files : Array[String]
)
{
  @option(symbol="o", description="outdir")
  var outDir : String = "temp"
}

