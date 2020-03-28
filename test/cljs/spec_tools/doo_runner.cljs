(ns spec-tools.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            spec-tools.core-test
            spec-tools.impl-test
            spec-tools.transform-test
            spec-tools.data-spec-test
            spec-tools.json-schema-test
            spec-tools.parse-test
            spec-tools.spec-test
            spec-tools.spell-test
            spec-tools.visitor-all-test
            spec-tools.visitor-test
            spec-tools.swagger.core-test
            spec-tools.swagger.spec-test
            spec-tools.spell-spec.alpha-test
            spec-tools.spell-spec.expound-test))

(enable-console-print!)

(doo-tests 'spec-tools.core-test
           'spec-tools.impl-test
           'spec-tools.transform-test
           'spec-tools.data-spec-test
           'spec-tools.json-schema-test
           'spec-tools.parse-test
           'spec-tools.spec-test
           'spec-tools.spell-test
           'spec-tools.visitor-all-test
           'spec-tools.visitor-test
           'spec-tools.swagger.core-test
           'spec-tools.swagger.spec-test
           'spec-tools.spell-spec.alpha-test
           'spec-tools.spell-spec.expound-test)
