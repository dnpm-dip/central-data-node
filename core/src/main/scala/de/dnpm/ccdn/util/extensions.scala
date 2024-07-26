package de.dnpm.ccdn.util


import scala.reflect.Enum


extension[T <: Enum](m: Map[T,String])
  def invert: Map[String,T] =
    m.map {
     (k,v) => (v,k)
    }

