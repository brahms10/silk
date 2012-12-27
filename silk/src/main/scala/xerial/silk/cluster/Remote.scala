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
// Remote.scala
// Since: 2012/12/20 2:22 PM
//
//--------------------------------------

package xerial.silk.cluster

import xerial.silk.cluster.SilkClient.{Register, Run}
import xerial.core.log.Logger
import xerial.silk.core.SilkSerializer
import xerial.lens.TypeUtil
import runtime.BoxedUnit
import xerial.core.util.DataUnit




/**
 * Remote command launcher
 * @author Taro L. Saito
 */
object Remote extends Logger {

  /**
   * Run the given function at the specified host
   * @param host
   * @param f
   * @tparam R
   * @return
   */
  def at[R](host:Host)(f: => R) : R = {
    val classBox = ClassBox.current

    val localClient = SilkClient.getClientAt(localhost.address)
    localClient ! Register(classBox)

    // Get remote client
    info("getting remote client at %s", host.address)
    val client = SilkClient.getClientAt(host.address)
    // TODO Support functions with arguments
    // Send a remote command request
    val ser = SilkSerializer.serializeClosure(f)
    debug("closure size: %s", DataUnit.toHumanReadableFormat(ser.length))
    client ! Run(classBox, ser)

    // TODO retrieve result
    null.asInstanceOf[R]
  }

  private[cluster] def run(r:Run) {
    info("Running command at %s", localhost)

    val myClassBox = r.cb.resolve
    run(myClassBox.classLoader, r.closure)
  }

  private[cluster] def run(cl:ClassLoader, closureBinary:Array[Byte]) {
    ClassBox.withClassLoader(cl) {
      val closure = SilkSerializer.deserializeClosure(closureBinary)
      val mainClass = closure.getClass
      info("deserialized the closure: class %s", mainClass)
      for(m <- mainClass.getMethods.filter(mt => mt.getName == "apply" & mt.getParameterTypes.length == 0).headOption) {
        info("invoke method: %s, className:%s", m.getName, mainClass)
        m.invoke(closure)
      }
    }
  }

}