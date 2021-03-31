package zio.slides

import com.raquo.airstream.core.Signal

case class Step($step: Signal[Int]) {
  def equalTo(int: Int): Signal[Boolean] =
    $step.map(_ == int)

  def lessThan(int: Int): Signal[Boolean] =
    $step.map(_ < int)

  def greaterThan(int: Int): Signal[Boolean] =
    $step.map(_ > int)

  def between(min: Int, max: Int): Signal[Boolean] =
    $step.map(step => step >= min && step <= max)
}
