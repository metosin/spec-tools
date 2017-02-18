(ns spec-tools.types
  (:require [spec-tools.impl :as impl]))

(defn- error-message [x]
  (str
    "Can't resolve type of a spec: '" x "'. "
    "You need to provide a `:hint` for the spec or "
    "add a dispatch function for `spec-tools.types/resolve-type`."))

(defn- dispatch [x]
  (cond

    ;; symbol
    (symbol? x)
    (impl/clojure-core-symbol-or-any x)

    ;; a from
    (seq? x)
    (impl/clojure-core-symbol-or-any (first x))

    ;; default
    :else x))

(defmulti resolve-type #'dispatch :default ::default)

(defmethod resolve-type ::default [x]
  (println (error-message x)))

(defn resolve-type-or-fail [x]
  (if (contains? (methods resolve-type) x)
    (resolve-type x)
    (throw (ex-info (error-message x) {:spec x}))))

(defmethod resolve-type 'clojure.core/any? [_] nil)
(defmethod resolve-type 'clojure.core/some? [_] nil)
(defmethod resolve-type 'clojure.core/number? [_] nil)
(defmethod resolve-type 'clojure.core/integer? [_] :long)
(defmethod resolve-type 'clojure.core/int? [_] :long)
(defmethod resolve-type 'clojure.core/pos-int? [_] :long)
(defmethod resolve-type 'clojure.core/neg-int? [_] :long)
(defmethod resolve-type 'clojure.core/nat-int? [_] :long)
(defmethod resolve-type 'clojure.core/float? [_] :double)
(defmethod resolve-type 'clojure.core/double? [_] :double)
(defmethod resolve-type 'clojure.core/boolean? [_] :boolean)
(defmethod resolve-type 'clojure.core/string? [_] :string)
(defmethod resolve-type 'clojure.core/ident? [_] :keyword)
(defmethod resolve-type 'clojure.core/simple-ident? [_] :keyword)
(defmethod resolve-type 'clojure.core/qualified-ident? [_] :keyword)
(defmethod resolve-type 'clojure.core/keyword? [_] :keyword)
(defmethod resolve-type 'clojure.core/simple-keyword? [_] :keyword)
(defmethod resolve-type 'clojure.core/qualified-keyword? [_] :keyword)
(defmethod resolve-type 'clojure.core/symbol? [_] :symbol)
(defmethod resolve-type 'clojure.core/simple-symbol? [_] :symbol)
(defmethod resolve-type 'clojure.core/qualified-symbol? [_] :symbol)
(defmethod resolve-type 'clojure.core/uuid? [_] :uuid)
(defmethod resolve-type 'clojure.core/uri? [_] :uri)
(defmethod resolve-type 'clojure.core/bigdec? [_] :bigdec)
(defmethod resolve-type 'clojure.core/inst? [_] :date)
(defmethod resolve-type 'clojure.core/seqable? [_] nil)
(defmethod resolve-type 'clojure.core/indexed? [_] nil)
(defmethod resolve-type 'clojure.core/map? [_] nil)
(defmethod resolve-type 'clojure.core/vector? [_] nil)
(defmethod resolve-type 'clojure.core/list? [_] nil)
(defmethod resolve-type 'clojure.core/seq? [_] nil)
(defmethod resolve-type 'clojure.core/char? [_] nil)
(defmethod resolve-type 'clojure.core/set? [_] nil)
(defmethod resolve-type 'clojure.core/nil? [_] :nil)
(defmethod resolve-type 'clojure.core/false? [_] :boolean)
(defmethod resolve-type 'clojure.core/true? [_] :boolean)
(defmethod resolve-type 'clojure.core/zero? [_] :long)
(defmethod resolve-type 'clojure.core/rational? [_] :long)
(defmethod resolve-type 'clojure.core/coll? [_] nil)
(defmethod resolve-type 'clojure.core/empty? [_] nil)
(defmethod resolve-type 'clojure.core/associative? [_] nil)
(defmethod resolve-type 'clojure.core/sequential? [_] nil)
(defmethod resolve-type 'clojure.core/ratio? [_] :ratio)
(defmethod resolve-type 'clojure.core/bytes? [_] nil)

(defmethod resolve-type 'clojure.spec/keys [_] :map)
