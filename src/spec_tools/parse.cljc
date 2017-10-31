(ns spec-tools.parse
  (:require [spec-tools.impl :as impl]
            [clojure.spec.alpha :as s]))

(declare parse-form)

(defn parse-spec
  "Parses info out of a spec. Spec can be passed as a name, Spec or a form.
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
    (parse-form (impl/normalize-symbol x) nil)

    ;; a from
    (seq? x)
    (parse-form (impl/normalize-symbol (first x)) x)

    ;; a spec
    (s/spec? x)
    (recur (s/form x))

    ;; default
    :else (parse-form x nil)))

(defmulti parse-form (fn [dispath _] dispath) :default ::default)

(defmethod parse-form ::default [_ _] {:type nil})

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
  (-> parse-form
      methods
      keys
      (->> (filter symbol?))
      set))

(defmethod parse-form 'clojure.core/any? [_ _])
(defmethod parse-form 'clojure.core/some? [_ _])
(defmethod parse-form 'clojure.core/number? [_ _] {:type :double})
(defmethod parse-form 'clojure.core/integer? [_ _] {:type :long})
(defmethod parse-form 'clojure.core/int? [_ _] {:type :long})
(defmethod parse-form 'clojure.core/pos-int? [_ _] {:type :long})
(defmethod parse-form 'clojure.core/pos-int? [_ _] {:type :long})
(defmethod parse-form 'clojure.core/neg-int? [_ _] {:type :long})
(defmethod parse-form 'clojure.core/nat-int? [_ _] {:type :long})
(defmethod parse-form 'clojure.core/float? [_ _] {:type :double})
(defmethod parse-form 'clojure.core/double? [_ _] {:type :double})
(defmethod parse-form 'clojure.core/boolean? [_ _] {:type :boolean})
(defmethod parse-form 'clojure.core/string? [_ _] {:type :string})
(defmethod parse-form 'clojure.core/ident? [_ _] {:type :keyword})
(defmethod parse-form 'clojure.core/simple-ident? [_ _] {:type :keyword})
(defmethod parse-form 'clojure.core/qualified-ident? [_ _] {:type :keyword})
(defmethod parse-form 'clojure.core/keyword? [_ _] {:type :keyword})
(defmethod parse-form 'clojure.core/simple-keyword? [_ _] {:type :keyword})
(defmethod parse-form 'clojure.core/qualified-keyword? [_ _] {:type :keyword})
(defmethod parse-form 'clojure.core/symbol? [_ _] {:type :symbol})
(defmethod parse-form 'clojure.core/simple-symbol? [_ _] {:type :symbol})
(defmethod parse-form 'clojure.core/qualified-symbol? [_ _] {:type :symbol})
(defmethod parse-form 'clojure.core/uuid? [_ _] {:type :uuid})
#?(:clj (defmethod parse-form 'clojure.core/uri? [_ _] {:type :uri}))
#?(:clj (defmethod parse-form 'clojure.core/decimal? [_ _] {:type :bigdec}))
(defmethod parse-form 'clojure.core/inst? [_ _] {:type :date})
(defmethod parse-form 'clojure.core/seqable? [_ _])
(defmethod parse-form 'clojure.core/indexed? [_ _])
(defmethod parse-form 'clojure.core/map? [_ _])
(defmethod parse-form 'clojure.core/vector? [_ _])
(defmethod parse-form 'clojure.core/list? [_ _])
(defmethod parse-form 'clojure.core/seq? [_ _])
(defmethod parse-form 'clojure.core/char? [_ _])
(defmethod parse-form 'clojure.core/set? [_ _])
(defmethod parse-form 'clojure.core/nil? [_ _])
(defmethod parse-form 'clojure.core/false? [_ _] {:type :boolean})
(defmethod parse-form 'clojure.core/true? [_ _] {:type :boolean})
(defmethod parse-form 'clojure.core/zero? [_ _] {:type :long})
#?(:clj (defmethod parse-form 'clojure.core/rational? [_ _] {:type :long}))
(defmethod parse-form 'clojure.core/coll? [_ _])
(defmethod parse-form 'clojure.core/empty? [_ _])
(defmethod parse-form 'clojure.core/associative? [_ _] {:type nil})
(defmethod parse-form 'clojure.core/sequential? [_ _] {:type nil})
#?(:clj (defmethod parse-form 'clojure.core/ratio? [_ _] {:type :ratio}))
#?(:clj (defmethod parse-form 'clojure.core/bytes? [_ _]))

(defmethod parse-form :clojure.spec.alpha/unknown [_ _])

(defmethod parse-form 'clojure.spec.alpha/keys [_ form]
  (let [{:keys [req opt req-un opt-un]} (impl/parse-keys form)]
    (cond-> {:type :map
             :keys (set (concat req opt req-un opt-un))}
            (or req req-un) (assoc :keys/req (set (concat req req-un)))
            (or opt opt-un) (assoc :keys/opt (set (concat opt opt-un))))))

(defmethod parse-form 'clojure.spec.alpha/or [_ _])

(defmethod parse-form 'clojure.spec.alpha/and [_ form]
  (parse-spec (second form)))

(defmethod parse-form 'clojure.spec.alpha/merge [_ form]
  (apply impl/deep-merge (map parse-spec (rest form))))

(defmethod parse-form 'clojure.spec.alpha/every [_ form]
  (let [{:keys [into]} (apply hash-map (drop 2 form))]
    {:type
     (cond
       (map? into) :map
       (set? into) :set
       :else :vector)}))

; every-ks

(defmethod parse-form 'clojure.spec.alpha/coll-of [_ form]
  (let [{:keys [into]} (apply hash-map (drop 2 form))]
    {:type
     (cond
       (map? into) :map
       (set? into) :set
       :else :vector)}))

(defmethod parse-form 'clojure.spec.alpha/map-of [_ _] {:type :map})

(defmethod parse-form 'spec-tools.core/spec [_ form]
  (parse-spec (-> form last :spec)))

; *
; +
; ?
; alt
; cat
; &
; tuple
; keys*
; nilable
