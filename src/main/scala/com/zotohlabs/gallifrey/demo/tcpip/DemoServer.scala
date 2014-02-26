/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/


package demo.tcpip

import java.io.{DataInputStream, BufferedInputStream}
import com.zotohlabs.gallifrey.runtime.AppMain
import com.zotohlabs.gallifrey.core.Container
import com.zotohlabs.gallifrey.io.{SocketEvent, TimerEvent}

import com.zotohlabs.wflow.core.Job
import com.zotohlabs.wflow._
import org.json.JSONObject


/**
 * @author kenl
 *
 */
class DemoMain extends AppMain {
  def contextualize(c:Container) {
  }
  def initialize() {
    println("Demo sending & receiving messages via sockets..." )
  }
  def configure(cfg:JSONObject) {
  }
  def start() {}
  def stop() {
  }
  def dispose() {
  }
}

class Demo extends PipelineDelegate {

  def onError(err:Throwable, curPt:FlowPoint) = null
  def onStop(pipe:Pipeline) {
  }

  private var _clientMsg=""

  val task1= new Work() {
      def perform(cur:FlowPoint, job:Job, arg:Any) = {

          val ev= job.event.asInstanceOf[SocketEvent]
          val sockBin = { (ev:SocketEvent)  =>
            val clen=new DataInputStream(ev.getSockIn).readInt()
            val bf= new BufferedInputStream( ev.getSockIn )
            val buf= new Array[Byte](clen)
            bf.read(buf)
            _clientMsg=new String(buf,"utf-8")
          }
          sockBin(ev)
          // add a delay into the workflow before next step
          new Delay(1500)
      }
  }

  val task2= new Work() {
      def perform(cur:FlowPoint, job:Job, arg:Any) = {
        println("Socket Server Received: " + _clientMsg )
        null
      }
  }

  def getStartActivity(pipe:Pipeline) = new PTask(task1).chain(new PTask(task2))

}

