package de.dnpm.ccdn.core.bfarm


import play.api.libs.json.{
  Json,
  Format
}


object Chromosome extends Enumeration
{

  val chr1  = Value("1")
  val chr2  = Value("2")
  val chr3  = Value("3")
  val chr4  = Value("4")
  val chr5  = Value("5")
  val chr6  = Value("6")
  val chr7  = Value("7")
  val chr8  = Value("8")
  val chr9  = Value("9")
  val chr10 = Value("10")
  val chr11 = Value("11")
  val chr12 = Value("12")
  val chr13 = Value("13")
  val chr14 = Value("14")
  val chr15 = Value("15")
  val chr16 = Value("16")
  val chr17 = Value("17")
  val chr18 = Value("18")
  val chr19 = Value("19")
  val chr20 = Value("20")
  val chr21 = Value("21")
  val chr22 = Value("22")
  val chrX  = Value("X")
  val chrY  = Value("Y")
  val chrMT = Value("MT")


  implicit val format: Format[Value] =
    Json.formatEnum(this)
}

