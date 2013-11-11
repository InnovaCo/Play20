logLevel := Level.Warn

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.5")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.0.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.6.0")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.4.0")

libraryDependencies <+= sbtVersion { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}

resolvers += "Innova plugins repo" at "http://repproxy.srv.inn.ru/artifactory/plugins-release-local"

addSbtPlugin("ru.inn" % "inn-sbt-builder-plugin.feature-our-play-building" % "latest.release")