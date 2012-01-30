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

package xerial.silk.remote

import sys.SystemProperties
import sys.process.Process
import java.io.File
import xerial.silk.util._
import collection.mutable.{HashMap, WeakHashMap}
import java.lang.{IllegalStateException, UnknownError}

//--------------------------------------
//
// JVMLauncher.scala
// Since: 2012/01/30 11:51
//
//--------------------------------------

/**
 * Launches Java VM for multi-JVM task execution
 * @author leo
 */
object JVMLauncher extends Logging {

  def launchJava(args: String) = {
    val javaCmd = findJavaCommand()
    if (javaCmd.isEmpty)
      throw new IllegalStateException("No JVM is found. Set JAVA_HOME environmental variable")

    val cmdLine = "%s %s".format(javaCmd.get, args)

    debug("Run command: " + cmdLine)

    Process(CommandLineTokenizer.tokenize(cmdLine)).run()
  }


  def launchProcess(cmdLine: String) = {
    val c = "%s -c \"%s\"".format(getCommand("sh"), cmdLine)
    debug {
      "exec command: " + c
    }

    val env = getEnv + ("CYGWIN" -> "notty")
    Process(CommandLineTokenizer.tokenize(c), None, env.toSeq:_*).run
  }

  def getEnv : Map[String, String] = {
    import collection.JavaConversions._
    System.getenv().toMap
  }

  def launchProcessInWindows(cmdLine: String) = {
    val c = "%s /c \"%s\"".format(getCommand("cmd"), cmdLine)
    debug {
      "exec command: " + c
    }

    Process(CommandLineTokenizer.tokenize(c), None, getEnv.toSeq:_*).run
  }


  /**
   * Return OS-dependent program name. (e.g., sh in Unix, sh.exe in Windows)
   */
  private def progName(p: String) = {
    if (OSInfo.isWindows)
      p + ".exe"
    else
      p
  }


  // command name -> path
  private val cmdPathCache = new WeakHashMap[String, Option[String]]

  def getCommand(name: String): String = {

    findCommand(name) match {
      case Some(cmd) => cmd
      case None => throw new IllegalStateException("Command not found: %s".format(name))
    }
  }

  def findSh: Option[String] = {
    findCommand("sh")
  }

  def getExecPath = {
    val path = (System.getenv("PATH") match {
      case null => ""
      case x => x.toString
    }).split("[;:]")
    path
  }

  def findCommand(name: String): Option[String] = {
    cmdPathCache.getOrElseUpdate(name, {
      val path = {
        if (OSInfo.isWindows)
          getExecPath ++ Seq("c:/cygwin/bin")
        else
          getExecPath
      }
      val prog = progName(name)

      val exe = path.map(new File(_, prog)).find(_.exists).map(_.getAbsolutePath)
      debug {
        if (exe.isDefined) "%s is found at %s".format(name, exe.get)
        else "%s is not found".format(name)
      }
      exe
    })
  }

  def findJavaHome: Option[String] = {
    // lookup environment variable JAVA_HOME first
    val e = System.getenv("JAVA_HOME")

    // If JAVA_HOME is not defined, use java.home system property
    if (e == null) {
      return System.getProperty("java.home") match {
        case null => None
        case x => Some(x)
      }
    }

    def resolveCygpath(p: String) = {
      if (OSInfo.isWindows) {
        // If the path is for Cygwin environment
        val m = """/cygdrive/(\w)(/.*)""".r.findFirstMatchIn(e)
        if (m.isDefined)
          "%s:%s".format(m.get.group(1), m.get.group(2))
        else
          p
      }
      else
        p
    }
    val p = Some(resolveCygpath(e))
    debug("Found JAVA_HOME=" + p.get)
    p
  }


  def findJavaCommand(javaCmdName: String = "java"): Option[String] = {


    def search: Option[String] = {
      def javaBin(java_home: String) = java_home + "/bin/" + progName(javaCmdName)

      def hasJavaCommand(java_home: String): Boolean = {
        val java_path = new File(javaBin(java_home))
        java_path.exists()
      }

      val java_home: Option[String] = {
        val e = findJavaHome

        import OS._
        e match {
          case Some(x) => e
          case None => {
            def listJDKIn(path: String) = {
              // TODO Oracle JVM (JRockit) support
              new File(path).listFiles().
                filter(x => x.isDirectory
                && (x.getName.startsWith("jdk") || x.getName.startsWith("jre"))
                && hasJavaCommand(x.getAbsolutePath)).map(_.getAbsolutePath)
            }
            def latestJDK(jdkPath: Array[String]): Option[String] = {
              if (jdkPath.isEmpty)
                None
              else {
                // TODO parse version number
                val sorted = jdkPath.sorted.reverse
                Some(sorted(0))
              }
            }
            debug("No java command found. Searching for JDK...")


            OSInfo.getOSType match {
              case Windows => latestJDK(listJDKIn("c:/Program Files/Java"))
              case Mac => {
                val l = Seq("/System/Library/Frameworkds/JavaVM.framework/Home", "/System/Library/Frameworkds/JavaVM.framework/Versions/CurrentJDK/Home").
                  filter(hasJavaCommand)
                if (l.isEmpty)
                  None
                else
                  Some(l(0))
              }
              case _ => None
            }
          }
        }
      }

      val ret: Option[String] = java_home match {
        case Some(x) => Some(javaBin(x).trim)
        case None => {
          val javaPath = (Process("which java") !!).trim
          if (javaPath.isEmpty)
            None
          else
            Some(javaPath)
        }
      }
      ret
    }

    cmdPathCache.getOrElseUpdate(javaCmdName, search)
  }

}