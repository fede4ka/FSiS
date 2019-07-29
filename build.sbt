name := "FSiS"

version := "0.1"

scalaVersion := "2.13.0"

//
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
//Kind-projector

//
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
//ScalaCheck