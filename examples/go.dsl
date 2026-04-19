let REGISTRY = "docker.io"

stage package as build {
  base chainguard {
    variant dev
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/go:1.24-dev"
  }

  workdir "/go/src/region-api"
  user "root"

  update

  tools {
    build [ git, ca-certificates, openssl, build-base ]
  }

  copy ["./"] to "./"

  tool go {
    build true
  }

  produces artifact region_api at "/go/src/region-api/region-api"
}

stage image as image {
  base chainguard {
    variant runtime
    distro  wolfi
    image   "{{REGISTRY}}/base-images/chainguard/go:1.24"
  }

  workdir "/go/src/region-api"
  user "nonroot"

  copy artifact package.region_api as runtime_region_api to "/go/src/region-api/region-api"

  carry tool git
  carry tool ca-certificates

  runtime binary {
    entry runtime_region_api
  }

  expose 3000
}
