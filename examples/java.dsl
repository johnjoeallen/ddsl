let REGISTRY = "docker.io"

stage package as build {
  base chainguard {
    variant dev
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/openjdk:24.0.1-dev"
  }

  workdir "/app"
  user "root"

  copy ["mvnw", "pom.xml"] to "./"

  tool maven {
    goals ["dependency:go-offline"]
  }

  copy ["src", "spec"] to "./"

  tool maven {
    goals     ["package"]
    skipTests true
  }

  produces artifact application at "/app/target/application-*.jar"
}

stage image as image {
  base chainguard {
    variant runtime
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/openjdk:24.0.1"
  }

  workdir "/app"
  user "nonroot"

  copy artifact package.application as app to "/app/app.jar"

  runtime java {
    jar "/app/app.jar"
  }

  expose 8000
}
