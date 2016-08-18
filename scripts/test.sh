#!/bin/bash
set -e
case $1 in
    cljs)
        lein trampoline run -m clojure.main scripts/build.clj
        node target/generated/js/out/tests.js
        ;;
    clj)
        lein test-clj
        ;;
    *)
        echo "Please select [clj|cljs]"
        exit 1
        ;;
esac
