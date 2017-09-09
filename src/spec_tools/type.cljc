(ns spec-tools.type
  (:require [spec-tools.impl :as impl]
            [clojure.spec.alpha :as s]))

(declare analyze)

(defn collect-info
  "Collects info of a spec. Info is either `nil` or a map,
  with keys `:type` and other extra keys (like `:keys` for
  s/keys specs)."
  [x]
  (cond

    ;; unknown
    (= ::s/unknown x)
    nil

    ;; spec name
    (qualified-keyword? x)
    (recur (s/form (s/get-spec x)))

    ;; symbol
    (symbol? x)
    (analyze (impl/clojure-core-symbol-or-any x) nil)

    ;; a from
    (seq? x)
    (analyze (impl/clojure-core-symbol-or-any (first x)) x)

    ;; a spec
    (s/spec? x)
    (recur (s/form x))

    ;; default
    :else (analyze x nil)))

(defn resolve-type
  "Resolves a type of a spec. Can be `nil`."
  [x] (:type (collect-info x)))

(defmulti analyze (fn [dispath _] dispath) :default ::default)

(defmethod analyze ::default [_ _] {:type nil})

(defn types []
  #{:long
    :double
    :boolean
    :string
    :keyword
    :symbol
    :uuid
    :uri
    :bigdec
    :date
    :ratio
    :map
    :set
    :vector})

(defn type-symbols []
  (-> analyze
      methods
      keys
      (->> (filter symbol?))
      set))

(defmethod analyze 'clojure.core/any? [_ _])
(defmethod analyze 'clojure.core/some? [_ _])
(defmethod analyze 'clojure.core/number? [_ _] {:type :double})
(defmethod analyze 'clojure.core/integer? [_ _] {:type :long})
(defmethod analyze 'clojure.core/int? [_ _] {:type :long})
(defmethod analyze 'clojure.core/pos-int? [_ _] {:type :long})
(defmethod analyze 'clojure.core/pos-int? [_ _] {:type :long})
(defmethod analyze 'clojure.core/neg-int? [_ _] {:type :long})
(defmethod analyze 'clojure.core/nat-int? [_ _] {:type :long})
(defmethod analyze 'clojure.core/float? [_ _] {:type :double})
(defmethod analyze 'clojure.core/double? [_ _] {:type :double})
(defmethod analyze 'clojure.core/boolean? [_ _] {:type :boolean})
(defmethod analyze 'clojure.core/string? [_ _] {:type :string})
(defmethod analyze 'clojure.core/ident? [_ _] {:type :keyword})
(defmethod analyze 'clojure.core/simple-ident? [_ _] {:type :keyword})
(defmethod analyze 'clojure.core/qualified-ident? [_ _] {:type :keyword})
(defmethod analyze 'clojure.core/keyword? [_ _] {:type :keyword})
(defmethod analyze 'clojure.core/simple-keyword? [_ _] {:type :keyword})
(defmethod analyze 'clojure.core/qualified-keyword? [_ _] {:type :keyword})
(defmethod analyze 'clojure.core/symbol? [_ _] {:type :symbol})
(defmethod analyze 'clojure.core/simple-symbol? [_ _] {:type :symbol})
(defmethod analyze 'clojure.core/qualified-symbol? [_ _] {:type :symbol})
(defmethod analyze 'clojure.core/uuid? [_ _] {:type :uuid})
#?(:clj (defmethod analyze 'clojure.core/uri? [_ _] {:type :uri}))
#?(:clj (defmethod analyze 'clojure.core/bigdec? [_ _] {:type :bigdec}))
(defmethod analyze 'clojure.core/inst? [_ _] {:type :date})
(defmethod analyze 'clojure.core/seqable? [_ _])
(defmethod analyze 'clojure.core/indexed? [_ _])
(defmethod analyze 'clojure.core/map? [_ _])
(defmethod analyze 'clojure.core/vector? [_ _])
(defmethod analyze 'clojure.core/list? [_ _])
(defmethod analyze 'clojure.core/seq? [_ _])
(defmethod analyze 'clojure.core/char? [_ _])
(defmethod analyze 'clojure.core/set? [_ _])
(defmethod analyze 'clojure.core/nil? [_ _])
(defmethod analyze 'clojure.core/false? [_ _] {:type :boolean})
(defmethod analyze 'clojure.core/true? [_ _] {:type :boolean})
(defmethod analyze 'clojure.core/zero? [_ _] {:type :long})
#?(:clj (defmethod analyze 'clojure.core/rational? [_ _] {:type :long}))
(defmethod analyze 'clojure.core/coll? [_ _])
(defmethod analyze 'clojure.core/empty? [_ _])
(defmethod analyze 'clojure.core/associative? [_ _] {:type nil})
(defmethod analyze 'clojure.core/sequential? [_ _] {:type nil})
#?(:clj (defmethod analyze 'clojure.core/ratio? [_ _] {:type :ratio}))
#?(:clj (defmethod analyze 'clojure.core/bytes? [_ _]))

(defmethod analyze :clojure.spec.alpha/unknown [_ _])

(defmethod analyze 'clojure.spec.alpha/keys [_ form]
  (let [{:keys [req opt req-un opt-un]} (some->> form (rest) (apply hash-map))]
    {:type :map
     :keys (set
             (flatten
               (concat
                 (map impl/polish (concat req opt))
                 (map impl/polish-un (concat req-un opt-un)))))}))

(defmethod analyze 'clojure.spec.alpha/or [_ _])

(defmethod analyze 'clojure.spec.alpha/and [_ form]
  (let [[_ predicate & _] form]
    (collect-info predicate)))

; merge

(defmethod analyze 'clojure.spec.alpha/every [_ form]
  (let [{:keys [into]} (apply hash-map (drop 2 form))]
    {:type
     (cond
       (map? into) :map
       (set? into) :set
       :else :vector)}))

; every-ks

(defmethod analyze 'clojure.spec.alpha/coll-of [_ form]
  (let [{:keys [into]} (apply hash-map (drop 2 form))]
    {:type
     (cond
       (map? into) :map
       (set? into) :set
       :else :vector)}))

(defmethod analyze 'clojure.spec.alpha/map-of [_ _] {:type :map})

; *
; +
; ?
; alt
; cat
; &
; tuple
; keys*
; nilable
