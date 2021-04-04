package com.klibisz.annblucene

import org.scalatest.Informer

import scala.concurrent.duration.Duration

trait Timing {

  protected def info: Informer

  def timeInfo[T](description: String)(run: => T): T = {
    val t0 = System.nanoTime()
    info(s"Starting [$description]")
    val t   = run
    val dur = Duration.fromNanos(System.nanoTime() - t0)
    val showDur =
      if (dur.toSeconds < 1) s"${dur.toMillis} millis"
      else if (dur.toMinutes < 1) s"${dur.toSeconds} seconds"
      else s"${dur.toMinutes} minutes"
    info(s"Completed [$description] in $showDur")
    t
  }

}
