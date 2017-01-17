(ns spec-tools.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            spec-tools.core-test
            spec-tools.json-schema-test
            spec-tools.visitor-test))

(enable-console-print!)

(doo-tests 'spec-tools.core-test
           'spec-tools.json-schema-test
           'spec-tools.visitor-test)
