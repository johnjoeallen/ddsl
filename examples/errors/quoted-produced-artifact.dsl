stage package as build {
  base chainguard { variant dev distro wolfi image "img" }
  produces artifact "application" at "/app/target/application.jar"
}
