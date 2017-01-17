(ns spec-tools.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            spec-tools.core-test))

(doo-tests 'spec-tools.core-test)
