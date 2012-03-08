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

import collection.mutable.WeakHashMap
import java.lang.reflect.Field
import reflect.ScalaSignature
import xerial.silk.util.{Logging, TypeUtil}
import tools.scalap.scalax.rules.scalasig._

//--------------------------------------
//
// ObjectSchema.scala
// Since: 2012/01/17 10:05
//
//--------------------------------------


/**
 * Object information extractor
 */
object ObjectSchema {

  abstract class Type(val rawType: Class[_]) {
    def getSimpleName: String = rawType.getSimpleName
  }

  case class StandardType(valueType: Class[_]) extends Type(valueType) {
    override def toString = getSimpleName
  }

  case class GenericType(valueType: Class[_], genericTypes: Seq[Type]) extends Type(valueType) {
    override def toString = "%s[%s]".format(valueType.getSimpleName, genericTypes.map(_.getSimpleName).mkString(", "))
  }

  case class Parameter(name: String, valueType: Type) {
    val rawType = valueType.rawType

    override def toString = "%s:%s".format(name, valueType)
  }

  case class Method(name: String, argTypes: Array[Parameter], returnType: Type) {
    override def toString = "Method(%s, [%s], %s)".format(name, argTypes.mkString(", "), returnType)
  }

  case class Constructor(cl: Class[_], params: Array[Parameter]) {
    override def toString = "Constructor(%s, [%s])".format(cl.getSimpleName, params.mkString(", "))
  }



  implicit def toSchema(cl: Class[_]): ObjectSchema = ObjectSchema(cl)

  private val schemaTable = new WeakHashMap[Class[_], ObjectSchema]

  /**
   * Get the object schema of the specified type. This method caches previously created ObjectSchema instances, and
   * second call for the same type object return the cached entry.
   */
  def apply(cl: Class[_]): ObjectSchema = schemaTable.getOrElseUpdate(cl, new ObjectSchema(cl))

  def getSchemaOf(obj: Any): ObjectSchema = apply(obj.getClass)


  def findSignature(cl: Class[_]): Option[ScalaSig] = {
    def enclosingObject(cl: Class[_]): Option[Class[_]] = {
      val pos = cl.getName.lastIndexOf("$")
      val parentClassName = cl.getName.slice(0, pos)
      try {
        Some(Class.forName(parentClassName))
      }
      catch {
        case e => None
      }
    }

    val sig = ScalaSigParser.parse(cl)

    sig match {
      case Some(x) => sig
      case None => {
        enclosingObject(cl) match {
          case Some(x) => findSignature(x)
          case None => None
        }
      }
    }
  }

  private def findConstructor(cl: Class[_]): Option[Constructor] = {
    def findConstructor(sig: ScalaSig): Option[Constructor] = {
      val className = cl.getSimpleName
      val entries = (0 until sig.table.length).map(sig.parseEntry(_))
      import scala.tools.scalap.scalax.rules.scalasig
      def isTargetClass(t: scalasig.Type): Boolean = {
        t match {
          case TypeRefType(_, ClassSymbol(sinfo, _), _) => {
            //debug("className = %s, found name = %s", className, sinfo.name)
            sinfo.name == className
          }
          case _ => false
        }
      }
      entries.collectFirst {
        case m: MethodType if isTargetClass(m.resultType) =>
          Constructor(cl, findConstructorParameters(m, sig))
      }
    }

    def findConstructorParameters(mt: MethodType, sig: ScalaSig): Array[Parameter] = {
      val paramSymbols: Seq[MethodSymbol] = mt match {
        case MethodType(_, param: Seq[_]) => param.collect {
          case m: MethodSymbol => m
        }
        case _ => Seq.empty
      }

      toAttribute(paramSymbols, sig)
    }

    findSignature(cl) match {
      case Some(sig) => findConstructor(sig)
      case None => None
    }
  }

  private def toAttribute(param: Seq[MethodSymbol], sig: ScalaSig): Array[Parameter] = {
    val paramRefs = param.map(p => (p.name, sig.parseEntry(p.symbolInfo.info)))
    val paramSigs = paramRefs.map {
      case (name: String, t: TypeRefType) => (name, t)
    }

    val b = Array.newBuilder[Parameter]
    for ((name, typeSignature) <- paramSigs) {
      val t = resolveType(typeSignature)
      b += new Parameter(name, t)
    }
    b.result
  }

  def isOwnedByTargetClass(m: MethodSymbol, cl: Class[_]): Boolean = {
    m.symbolInfo.owner match {
      case ClassSymbol(symbolInfo, _) => symbolInfo.name == cl.getSimpleName
      case _ => false
    }
  }

  def parametersOf(cl: Class[_]): Array[Parameter] = {
    findSignature(cl) match {
      case None => Array.empty
      case Some(sig) => {
        val entries = (0 until sig.table.length).map(sig.parseEntry(_))

        val paramTypes = entries.collect {
          case m: MethodSymbol if m.isAccessor && isOwnedByTargetClass(m, cl) => {
            entries(m.symbolInfo.info) match {
              case NullaryMethodType(resultType: TypeRefType) => (m.name, resolveType(resultType))
            }
          }
        }

        paramTypes.map(each => new Parameter(each._1, each._2)).toArray
      }
    }
  }

  def methodsOf(cl: Class[_]): Array[Method] = {
    findSignature(cl) match {
      case None => Array.empty
      case Some(sig) => {
        val entries = (0 until sig.table.length).map(sig.parseEntry(_))

        def isTargetMethod(m: MethodSymbol): Boolean = {
          m.isMethod && !m.isAccessor && m.name != "<init>" && isOwnedByTargetClass(m, cl)
        }

        val methods = entries.collect {
          case m: MethodSymbol if isTargetMethod(m) => {
            val methodType = entries(m.symbolInfo.info)
            methodType match {
              case NullaryMethodType(resultType: TypeRefType) => Method(m.name, Array.empty, resolveType(resultType))
              case MethodType(resultType: TypeRefType, paramSymbols: Seq[_]) => {
                Method(m.name, toAttribute(paramSymbols.asInstanceOf[Seq[MethodSymbol]], sig), resolveType(resultType))
              }
            }
          }
        }
        methods.toArray
      }
    }
  }

  def resolveType(typeSignature: TypeRefType): Type = {
    val name = typeSignature.symbol.toString()
    val clazz: Class[_] = {
      try {
        name match {
          // Resolve primitive types.
          // This specital treatment is necessary because scala.Int etc. classes do exists but,
          // Scala compiler reduces AnyVal types (e.g., classOf[Int] etc) into Java primitive types (e.g., int, float)
          case "scala.Boolean" => classOf[Boolean]
          case "scala.Byte" => classOf[Byte]
          case "scala.Short" => classOf[Short]
          case "scala.Char" => classOf[Char]
          case "scala.Int" => classOf[Int]
          case "scala.Float" => classOf[Float]
          case "scala.Long" => classOf[Long]
          case "scala.Double" => classOf[Double]
          case "scala.Predef.String" => classOf[String]
          case "scala.Predef.Map" => classOf[Map[_, _]]
          case "scala.package.Seq" => classOf[Seq[_]]
          case _ => Class.forName(name)
        }
      }
      catch {
        case _ => throw new IllegalArgumentException("unknown type: " + name)
      }
    }

    if (typeSignature.typeArgs.isEmpty) {
      StandardType(clazz)
    }
    else {
      val typeArgs: Seq[Type] = typeSignature.typeArgs.map {
        case x: TypeRefType => resolveType(x)
      }
      GenericType(clazz, typeArgs)
    }
  }

}


/**
 * Contains information of methods, constructor and parameters defined in a class
 * @author leo
 */
class ObjectSchema(val cl: Class[_]) extends Logging {

  import ObjectSchema._

  val name: String = cl.getSimpleName
  val fullName: String = cl.getName

  def findSignature: Option[ScalaSig] = ObjectSchema.findSignature(cl)

  lazy val parameters: Array[Parameter] = parametersOf(cl)
  lazy val methods: Array[Method] = methodsOf(cl)

  lazy private val parameterIndex: Map[String, Parameter] = {
    val pair = for (a <- parameters) yield a.name -> a
    pair.toMap
  }

  def getParameter(name: String): Parameter = {
    parameterIndex(name)
  }


  lazy val constructor: Constructor = {
    findConstructor(cl) match {
      case Some(c) => c
      case None => throw new IllegalArgumentException("no constructor is found for " + cl)
    }
  }

  override def toString = {
    val b = new StringBuilder
    b append (cl.getSimpleName + "(")
    b append (parameters.mkString(", "))
    b.append(")")
    b.toString
  }


}

