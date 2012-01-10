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

package xerial.silk
package util

import annotation.ClassfileAnnotation

//--------------------------------------
//
// OptionParser.scala
// Since: 2012/01/10 13:43
//
//--------------------------------------


object OptionParser extends Logging {


  def parse[T](optionClass:Class[T], args:Array[String])(implicit m:Manifest[T]):T = {

    val opt = optionClass.getConstructor().newInstance()

    debug {
      val decl = optionClass.getDeclaredMethods
      println(decl.mkString("\n"))
    }
    //val a = optionClass.getAnnotations
    //println("decl anot: " + a.map(_.annotationType().getName).mkString("\n"))

    def printAnnotation(a:Any) {
      a match {
        case opt:option => debug(opt)
        case arg:argument => debug(arg)
        case _ => // ignore
      }
    }


    for(p <- optionClass.getDeclaredMethods; anot <- p.getDeclaredAnnotations) {
      printAnnotation(anot)
    }
    for(p <- optionClass.getDeclaredFields; anot <- p.getDeclaredAnnotations) {
      printAnnotation(anot)
    }






    opt
  }

}

/**
 * @author leo
 */
class OptionParser {


}