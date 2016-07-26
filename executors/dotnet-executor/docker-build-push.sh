#!/bin/bash 

set -e
# set -x

export IMG=galacticfog.artifactoryonline.com/lambda-dotnet-alt-executor

export SHA=$(git rev-parse --short=8 HEAD)
export VER=$(grep "^version" build.sbt | sed 's/.*:=[ ]*//' | sed 's/"//g')
export TAG=$VER-$SHA

echo "Building $TAG"

echo "Creating build image..."
sbt docker:stage
cd target/docker/stage
docker build -t $IMG:$TAG .
echo "Pushing new image to artifactory..."
docker push   $IMG:$TAG
docker tag  	$IMG:$TAG $IMG:$VER
docker push   $IMG:$VER
