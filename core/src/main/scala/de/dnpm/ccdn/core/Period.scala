package de.dnpm.ccdn.core


import java.time.temporal.Temporal


final case class Period[T <: Temporal]
(
  start: T,
  end: Option[T] = None
)

