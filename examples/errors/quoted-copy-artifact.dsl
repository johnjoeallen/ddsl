stage image as image {
  base chainguard { variant runtime distro wolfi image "img" }
  copy artifact "application" to "/app/app.jar"
}
