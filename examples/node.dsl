let REGISTRY = "docker.io"

stage package as build {
  base chainguard {
    variant dev
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/node:20.18.3-dev"
  }

  workdir "/usr/src/app"
  user "root"

  update

  tools {
    build [ curl, unzip, aws-cli ]
  }

  copy ["package*.json"] to "./"

  tool node {
    install prod
  }

  copy ["./"] to "./"

  produces artifact node_application at "/usr/src/app"
}

stage image as image {
  base chainguard {
    variant runtime
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/node:20.18.3-slim"
  }

  workdir "/usr/src/app"
  user "nonroot"

  copy artifact package.node_application as node_app to "/usr/src/app"

  carry tool aws-cli

  runtime node {
    entry "index.js"
  }

  expose 5000
}
