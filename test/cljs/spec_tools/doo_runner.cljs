(ns spec-tools.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            spec-tools.core-test
            spec-tools.conform-test
            spec-tools.data-spec-test
            spec-tools.json-schema-test
            spec-tools.visitor-all-test
            spec-tools.visitor-test
            spec-tools.swagger.core-test
            spec-tools.swagger.spec-test
            ))

(enable-console-print!)

(doo-tests 'spec-tools.core-test
           'spec-tools.conform-test
           'spec-tools.data-spec-test
           'spec-tools.json-schema-test
           'spec-tools.visitor-all-test
           'spec-tools.visitor-test
           'spec-tools.swagger.core-test
           'spec-tools.swagger.spec-test)
