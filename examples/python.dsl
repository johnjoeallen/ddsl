let REGISTRY = "docker.io"

stage package as build {
  base chainguard {
    variant dev
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/python:3.13.4-dev"
  }

  workdir "/usr/src/app"
  user "root"

  update

  copy ["requirements.txt"] to "./"

  tool python {
    pip {
      requirements "requirements.txt"
    }
  }

  copy ["./"] to "./"

  produces artifact python_application at "/usr/src/app"
}

stage image as image {
  base chainguard {
    variant runtime
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/python:3.13.4"
  }

  workdir "/usr/src/app"
  user "nonroot"

  copy artifact package.python_application as python_app to "/usr/src/app"

  runtime python {
    entry "app.py"
  }

  expose 8000
}
