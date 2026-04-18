let REGISTRY = "docker.io"

stage build-python as build {
  base chainguard {
    variant dev
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/python:3.13.4-dev"
  }

  workdir "/usr/src/app"
  user "root"

  copy ["requirements.txt"] to "./"

  tool python {
    pip {
      requirements "requirements.txt"
    }
  }

  copy ["./"] to "./"

  produces artifact "python-application" at "/usr/src/app"
}

stage image as image {
  base chainguard {
    variant runtime
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/python:3.13.4"
  }

  workdir "/usr/src/app"
  user "nonroot"

  copy artifact "python-application" as "python-app" to "/usr/src/app"

  runtime python {
    entry "app.py"
  }

  expose 8000
}
