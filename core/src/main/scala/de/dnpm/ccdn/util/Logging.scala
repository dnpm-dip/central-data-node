package de.dnpm.ccdn.util


import org.slf4j.{
  Logger,
  LoggerFactory
}


trait Logging:
 
  self =>

  val log = LoggerFactory.getLogger(self.getClass)

