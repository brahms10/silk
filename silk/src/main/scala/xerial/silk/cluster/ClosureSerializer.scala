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

//--------------------------------------
//
// ClosureSerializer.scala
// Since: 2012/12/28 0:22
//
//--------------------------------------

package xerial.silk.cluster

import java.io._
import xerial.silk.core.Silk
import java.lang.reflect.Constructor
import org.objectweb.asm.{MethodVisitor, Opcodes, ClassVisitor, ClassReader}
import collection.mutable.Set
import xerial.core.log.Logger
import xerial.silk.core.SilkSerializer.ObjectDeserializer
import xerial.core.util.DataUnit

object LazyF0 {
  def apply[R](f: => R) = new LazyF0(f)
}


/**
 * This class is used to obtain the class names of the call-by-name functions (Function0[R]).
 *
 * This wrapper do not directly access the field f (Function0[R]) in order
 * to avoid the evaluation of the function.
 * @param f
 * @tparam R
 */
class LazyF0[R](f: => R) {

  /**
   * Obtain the function class
   * @return
   */
  def functionClass : Class[_] = {
    val field = this.getClass.getDeclaredField("f")
    field.get(this).getClass
  }

  def functionInstance : Function0[R] = {
    this.getClass.getDeclaredField("f").get(this).asInstanceOf[Function0[R]]
  }
  /**
   * We never use this method, but this definition is necessary in order to let the compiler generate the private field 'f' that
   * holds a reference to the call-by-name function.
   * @return
   */
  def eval = f
}


/**
 * Closure serializer
 *
 * @author Taro L. Saito
 */
private[silk] object ClosureSerializer extends Logger {


  def cleanupClosure[R](f: LazyF0[R]) = {
    debug("cleanup closure")
    val cl = f.functionClass
    debug("closure class: %s", cl)

    val finder = new FieldAccessFinder
    getClassReader(cl).accept(finder, 0)

    debug("accessed fields: %s", finder.output)

    val clone = instantiateClass(cl)
    clone
  }

  /**
   * Find the accessed parameters of the target class in the closure.
   * This function is used for optimizing data retrieval in Silk.
   * @param target
   * @param closure
   * @return
   */
  def accessedFields(target:Class[_], closure:AnyRef) : Seq[String] = {
    val finder = new ObjectParamAccessFinder(target)
    getClassReader(closure.getClass).accept(finder, 0)
    finder.getAccessedParams
  }


  def serializeClosure[R](f: => R) = {
    val lf = LazyF0(f)
    debug("Serializing closure class %s", lf.functionClass)
    val clean = cleanupClosure(lf)
    val b = new ByteArrayOutputStream()
    val o = new ObjectOutputStream(b)
    o.writeObject(lf.functionInstance)
    o.flush()
    o.close
    b.close
    val ser = b.toByteArray
    debug("closure size: %s", DataUnit.toHumanReadableFormat(ser.length))
    ser
  }

  def deserializeClosure(b:Array[Byte]) : AnyRef = {
    val in = new ObjectDeserializer(new ByteArrayInputStream(b))
    val ret = in.readObject()
    in.close()
    ret
  }




  def instantiateClass(cl:Class[_]) : Any = {
    val m = classOf[ObjectStreamClass].getDeclaredMethod("getSerializableConstructor", classOf[Class[_]])
    m.setAccessible(true)
    val constructor = m.invoke(null, cl).asInstanceOf[Constructor[_]]
    constructor.newInstance()
  }



  private def getClassReader(cl: Class[_]): ClassReader = {
    new ClassReader(cl.getResourceAsStream(
      cl.getName.replaceFirst("^.*\\.", "") + ".class"))
  }

  private class ObjectParamAccessFinder(target:Class[_]) extends ClassVisitor(Opcodes.ASM4) {
    val accessed = Seq.newBuilder[String]
    def getAccessedParams = accessed.result
    override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]) = {
      trace("visit method: %s desc:%s", name, desc)
      new MethodVisitor(Opcodes.ASM4) {

        def clName(s:String) = s.replace("/", ".")

        override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
          trace("visit field insn: %d owner:%s name:%s desc:%s", opcode, owner, name, desc)
        }
        override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String) {
          trace("visit method insn: %d owner:%s name:%s desc:%s", opcode, owner, name, desc)
          if(opcode == Opcodes.INVOKEVIRTUAL && clName(owner) == target.getName) {
            accessed += name
          }
        }
      }
    }
  }




  private class FieldAccessFinder() extends ClassVisitor(Opcodes.ASM4) {
    val output = collection.mutable.Map[Class[_], Set[String]]()
    override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]) = {
      trace("visit method: %s desc:%s", name, desc)
      new MethodVisitor(Opcodes.ASM4) {

        def clName(s:String) = s.replace("/", ".")

        override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
          trace("visit field insn: %d owner:%s name:%s desc:%s", opcode, owner, name, desc)
          if(opcode == Opcodes.GETFIELD) {
            for(cl <- output.keys if cl.getName == clName(owner))
              output(cl) += name
          }
        }
        override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String) {
          trace("visit method insn: %d owner:%s name:%s desc:%s", opcode, owner, name, desc)
          if (opcode == Opcodes.INVOKEVIRTUAL && !owner.endsWith("$iwC") && !name.endsWith("$outer")) {
            for (cl <- output.keys if cl.getName == clName(owner))
              output(cl) += name
          }
        }
      }
    }
  }


}