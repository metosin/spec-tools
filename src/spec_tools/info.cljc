(ns spec-tools.info
  (:require [spec-tools.impl :as impl]
            [clojure.spec.alpha :as s]))

(declare extract-from-form)

(defn extract
  "Extracts info of a spec. Spec can be passed as a name, Spec or a form.
  Returns either `nil` or a map, with keys `:type` and other extra keys
  (like `:keys` for s/keys specs)."
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
    (extract-from-form (impl/clojure-core-symbol-or-any x) nil)

    ;; a from
    (seq? x)
    (extract-from-form (impl/clojure-core-symbol-or-any (first x)) x)

    ;; a spec
    (s/spec? x)
    (recur (s/form x))

    ;; default
    :else (extract-from-form x nil)))

(defmulti extract-from-form (fn [dispath _] dispath) :default ::default)

(defmethod extract-from-form ::default [_ _] {:type nil})

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
  (-> extract-from-form
      methods
      keys
      (->> (filter symbol?))
      set))

(defmethod extract-from-form 'clojure.core/any? [_ _])
(defmethod extract-from-form 'clojure.core/some? [_ _])
(defmethod extract-from-form 'clojure.core/number? [_ _] {:type :double})
(defmethod extract-from-form 'clojure.core/integer? [_ _] {:type :long})
(defmethod extract-from-form 'clojure.core/int? [_ _] {:type :long})
(defmethod extract-from-form 'clojure.core/pos-int? [_ _] {:type :long})
(defmethod extract-from-form 'clojure.core/pos-int? [_ _] {:type :long})
(defmethod extract-from-form 'clojure.core/neg-int? [_ _] {:type :long})
(defmethod extract-from-form 'clojure.core/nat-int? [_ _] {:type :long})
(defmethod extract-from-form 'clojure.core/float? [_ _] {:type :double})
(defmethod extract-from-form 'clojure.core/double? [_ _] {:type :double})
(defmethod extract-from-form 'clojure.core/boolean? [_ _] {:type :boolean})
(defmethod extract-from-form 'clojure.core/string? [_ _] {:type :string})
(defmethod extract-from-form 'clojure.core/ident? [_ _] {:type :keyword})
(defmethod extract-from-form 'clojure.core/simple-ident? [_ _] {:type :keyword})
(defmethod extract-from-form 'clojure.core/qualified-ident? [_ _] {:type :keyword})
(defmethod extract-from-form 'clojure.core/keyword? [_ _] {:type :keyword})
(defmethod extract-from-form 'clojure.core/simple-keyword? [_ _] {:type :keyword})
(defmethod extract-from-form 'clojure.core/qualified-keyword? [_ _] {:type :keyword})
(defmethod extract-from-form 'clojure.core/symbol? [_ _] {:type :symbol})
(defmethod extract-from-form 'clojure.core/simple-symbol? [_ _] {:type :symbol})
(defmethod extract-from-form 'clojure.core/qualified-symbol? [_ _] {:type :symbol})
(defmethod extract-from-form 'clojure.core/uuid? [_ _] {:type :uuid})
#?(:clj (defmethod extract-from-form 'clojure.core/uri? [_ _] {:type :uri}))
#?(:clj (defmethod extract-from-form 'clojure.core/bigdec? [_ _] {:type :bigdec}))
(defmethod extract-from-form 'clojure.core/inst? [_ _] {:type :date})
(defmethod extract-from-form 'clojure.core/seqable? [_ _])
(defmethod extract-from-form 'clojure.core/indexed? [_ _])
(defmethod extract-from-form 'clojure.core/map? [_ _])
(defmethod extract-from-form 'clojure.core/vector? [_ _])
(defmethod extract-from-form 'clojure.core/list? [_ _])
(defmethod extract-from-form 'clojure.core/seq? [_ _])
(defmethod extract-from-form 'clojure.core/char? [_ _])
(defmethod extract-from-form 'clojure.core/set? [_ _])
(defmethod extract-from-form 'clojure.core/nil? [_ _])
(defmethod extract-from-form 'clojure.core/false? [_ _] {:type :boolean})
(defmethod extract-from-form 'clojure.core/true? [_ _] {:type :boolean})
(defmethod extract-from-form 'clojure.core/zero? [_ _] {:type :long})
#?(:clj (defmethod extract-from-form 'clojure.core/rational? [_ _] {:type :long}))
(defmethod extract-from-form 'clojure.core/coll? [_ _])
(defmethod extract-from-form 'clojure.core/empty? [_ _])
(defmethod extract-from-form 'clojure.core/associative? [_ _] {:type nil})
(defmethod extract-from-form 'clojure.core/sequential? [_ _] {:type nil})
#?(:clj (defmethod extract-from-form 'clojure.core/ratio? [_ _] {:type :ratio}))
#?(:clj (defmethod extract-from-form 'clojure.core/bytes? [_ _]))

(defmethod extract-from-form :clojure.spec.alpha/unknown [_ _])

(defmethod extract-from-form 'clojure.spec.alpha/keys [_ form]
  (let [{:keys [req opt req-un opt-un]} (some->> form (rest) (apply hash-map))]
    {:type :map
     :keys (set
             (flatten
               (concat
                 (map impl/polish (concat req opt))
                 (map impl/polish-un (concat req-un opt-un)))))}))

(defmethod extract-from-form 'clojure.spec.alpha/or [_ _])

(defmethod extract-from-form 'clojure.spec.alpha/and [_ form]
  (extract (second form)))

; merge

(defmethod extract-from-form 'clojure.spec.alpha/every [_ form]
  (let [{:keys [into]} (apply hash-map (drop 2 form))]
    {:type
     (cond
       (map? into) :map
       (set? into) :set
       :else :vector)}))

; every-ks

(defmethod extract-from-form 'clojure.spec.alpha/coll-of [_ form]
  (let [{:keys [into]} (apply hash-map (drop 2 form))]
    {:type
     (cond
       (map? into) :map
       (set? into) :set
       :else :vector)}))

(defmethod extract-from-form 'clojure.spec.alpha/map-of [_ _] {:type :map})

; *
; +
; ?
; alt
; cat
; &
; tuple
; keys*
; nilable
