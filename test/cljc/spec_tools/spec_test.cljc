(ns spec-tools.spec-test
  (:require [clojure.test :refer [deftest testing is are]]
            [spec-tools.spec :as spec]))

(deftest specs-test
  (are [spec pred]
    (= (:spec spec) pred)
    spec/any? clojure.core/any?
    spec/some? clojure.core/some?
    spec/number? clojure.core/number?
    spec/pos? clojure.core/pos?
    spec/neg? clojure.core/neg?
    spec/integer? clojure.core/integer?
    spec/int? clojure.core/int?
    spec/pos-int? clojure.core/pos-int?
    spec/neg-int? clojure.core/neg-int?
    spec/nat-int? clojure.core/nat-int?
    spec/float? clojure.core/float?
    spec/double? clojure.core/double?
    spec/boolean? clojure.core/boolean?
    spec/string? clojure.core/string?
    spec/ident? clojure.core/ident?
    spec/simple-ident? clojure.core/simple-ident?
    spec/qualified-ident? clojure.core/qualified-ident?
    spec/keyword? clojure.core/keyword?
    spec/simple-keyword? clojure.core/simple-keyword?
    spec/qualified-keyword? clojure.core/qualified-keyword?
    spec/symbol? clojure.core/symbol?
    spec/simple-symbol? clojure.core/simple-symbol?
    spec/qualified-symbol? clojure.core/qualified-symbol?
    spec/uuid? clojure.core/uuid?
    #?@(:clj [spec/uri? clojure.core/uri?])
    #?@(:clj [spec/decimal? clojure.core/decimal?])
    spec/inst? clojure.core/inst?
    spec/seqable? clojure.core/seqable?
    spec/indexed? clojure.core/indexed?
    spec/map? clojure.core/map?
    spec/vector? clojure.core/vector?
    spec/list? clojure.core/list?
    spec/seq? clojure.core/seq?
    spec/char? clojure.core/char?
    spec/set? clojure.core/set?
    spec/nil? clojure.core/nil?
    spec/false? clojure.core/false?
    spec/true? clojure.core/true?
    spec/zero? clojure.core/zero?
    #?@(:clj [spec/rational? clojure.core/rational?])
    spec/coll? clojure.core/coll?
    spec/empty? clojure.core/empty?
    spec/associative? clojure.core/associative?
    spec/sequential? clojure.core/sequential?
    #?@(:clj [spec/ratio? clojure.core/ratio?])
    #?@(:clj [spec/bytes? clojure.core/bytes?])))
