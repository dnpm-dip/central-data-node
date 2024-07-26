package de.dnpm.ccdn.core


import java.time.LocalDateTime
import de.dnpm.ccdn.util.{
  SPI,
  SPILoader
}



trait RepoOps[Err]:

  def setLastPollingTime(
    site: Coding[Site],
    dt: LocalDateTime
  ): Either[Err,Boolean]


  def lastPollingTime(
    site: Coding[Site]
  ): Either[Err,LocalDateTime]


  def save(
    report: DNPM.SubmissionReport
  ): Either[Err,DNPM.SubmissionReport]

  def save(
    reports: Seq[DNPM.SubmissionReport]
  ): Either[Err,Seq[DNPM.SubmissionReport]]

  def submissionReports: Either[Err,List[DNPM.SubmissionReport]]

  def delete(
    report: DNPM.SubmissionReport
  ): Either[Err,DNPM.SubmissionReport]

/*
  def save(
    report: DataSubmissionReport
  ): Either[Err,DataSubmissionReport]

  def save(
    reports: Seq[DataSubmissionReport]
  ): Either[Err,Seq[DataSubmissionReport]]

  def dataSubmissionReports: Either[Err,List[DataSubmissionReport]]

  def delete(
    report: DataSubmissionReport
  ): Either[Err,DataSubmissionReport]
*/



type Repository = RepoOps[String]

trait RepositoryProvider extends SPI[Repository]

object Repository extends SPILoader[RepositoryProvider]
