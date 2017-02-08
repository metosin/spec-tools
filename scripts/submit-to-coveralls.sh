#!/bin/sh

COVERALLS_URL="https://coveralls.io/api/v1/jobs"
lein cloverage --coveralls
curl -F "json_file=@target/coverage/coveralls.json" "$COVERALLS_URL"
