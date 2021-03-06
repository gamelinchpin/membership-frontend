name := "membership-tests"

version := "1.1"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.10" % "2.0",
  "org.seleniumhq.selenium" % "selenium-java" % "2.42.2",
  "com.gu" %% "scala-automation" % "1.42" % "test",
  "com.gu" %% "scala-automation-tstash-logger" % "1.8" % "test"
)

resolvers ++= Seq(
  "Sonatype OSS Staging" at "https://oss.sonatype.org/content/repositories/staging"
)
