#!/bin/bash

http lambda.dev.galacticfog.com/lambdas | jq ".[] | .id" | sed -e 's/"//g' | xargs -I '{}' http DELETE lambda.dev.galacticfog.com/lambdas/'{}'
