(ns spec-tools.parse
  (:require [spec-tools.impl :as impl]
            [clojure.spec.alpha :as s]
            [spec-tools.form :as form]))

(declare parse-form)
(declare non-leaf-types)

(defn type-dispatch-value [type]
  ((if (sequential? type) first identity) type))

(defn collection-type? [type]
  (contains? #{:map :map-of :set :vector :tuple} type))

(defn leaf-type? [type]
  (not (contains? (non-leaf-types) type)))

(defn parse-spec
  "Parses info out of a spec. Spec can be passed as a name, Spec or a form.
  Returns either `nil` or a map, with keys `:type` and other extra keys
  (like `:keys` for s/keys specs)."
  ([x]
   (parse-spec x nil))
  ([x options]
   (cond

     ;; unknown
     (= ::s/unknown x)
     nil

     ;; spec name - handle in the two arity version to account for recursive calls
     (qualified-keyword? x)
     (if (get (::visited options) x)
       {:spec x}
       (parse-spec (s/form (s/get-spec x)) (update options ::visited (fnil conj #{}) x)))

     ;; symbol
     (symbol? x)
     (parse-form (impl/normalize-symbol x) nil options)

     ;; a form
     (seq? x)
     (parse-form (impl/normalize-symbol (first x)) x options)

     ;; a spec
     (s/spec? x)
     (recur (s/form x) options)

     ;; a predicate
     (ifn? x)
     (parse-form (impl/normalize-symbol (form/resolve-form x)) nil options)

     ;; default
     :else (parse-form x nil options))))

(defn parse-spec-with-spec-ref [x options]
  (merge (parse-spec x options) (if (qualified-keyword? x) {:spec x})))

(defn get-keys [parse-data]
  (or (::keys parse-data)
      (some->> parse-data ::items (keep get-keys) (apply concat) (seq) (set))))

(defmulti parse-form (fn [dispatch _ _] dispatch) :default ::default)

(defmethod parse-form ::default [_ _ _] {:type nil})

(defn- non-leaf-types []
  #{:map :map-of :and :or :nilable :tuple :set :vector :multi-spec})

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
    :map-of
    :multi-spec
    :and
    :or
    :set
    :tuple
    :nilable
    :vector
    :spec})

(defn type-symbols []
  (-> parse-form
      methods
      keys
      (->> (filter symbol?))
      set))

(defmethod parse-form 'clojure.core/any? [_ _ _] {:spec any?})
(defmethod parse-form 'clojure.core/some? [_ _ _] {:spec some?})
(defmethod parse-form 'clojure.core/number? [_ _ _] {:spec number?, :type :double})
(defmethod parse-form 'clojure.core/integer? [_ _ _] {:spec integer?, :type :long})
(defmethod parse-form 'clojure.core/int? [_ _ _] {:spec int?, :type :long})
(defmethod parse-form 'clojure.core/pos-int? [_ _ _] {:spec pos-int?, :type :long})
(defmethod parse-form 'clojure.core/neg-int? [_ _ _] {:spec neg-int?, :type :long})
(defmethod parse-form 'clojure.core/nat-int? [_ _ _] {:spec nat-int?, :type :long})
(defmethod parse-form 'clojure.core/float? [_ _ _] {:spec float?, :type :double})
(defmethod parse-form 'clojure.core/double? [_ _ _] {:spec double?, :type :double})
(defmethod parse-form 'clojure.core/boolean? [_ _ _] {:spec boolean?, :type :boolean})
(defmethod parse-form 'clojure.core/string? [_ _ _] {:spec string?, :type :string})
(defmethod parse-form 'clojure.core/ident? [_ _ _] {:spec ident? :type :keyword})
(defmethod parse-form 'clojure.core/simple-ident? [_ _ _] {:spec simple-ident?, :type :keyword})
(defmethod parse-form 'clojure.core/qualified-ident? [_ _ _] {:spec qualified-ident?, :type :keyword})
(defmethod parse-form 'clojure.core/keyword? [_ _ _] {:spec keyword?, :type :keyword})
(defmethod parse-form 'clojure.core/simple-keyword? [_ _ _] {:spec simple-keyword?, :type :keyword})
(defmethod parse-form 'clojure.core/qualified-keyword? [_ _ _] {:spec qualified-keyword? :type :keyword})
(defmethod parse-form 'clojure.core/symbol? [_ _ _] {:spec symbol?, :type :symbol})
(defmethod parse-form 'clojure.core/simple-symbol? [_ _ _] {:spec simple-symbol?, :type :symbol})
(defmethod parse-form 'clojure.core/qualified-symbol? [_ _ _] {:spec qualified-symbol?, :type :symbol})
(defmethod parse-form 'clojure.core/uuid? [_ _ _] {:spec uuid?, :type :uuid})
#?(:clj (defmethod parse-form 'clojure.core/uri? [_ _ _] {:spec uri?, :type :uri}))
#?(:clj (defmethod parse-form 'clojure.core/decimal? [_ _ _] {:spec decimal?, :type :bigdec}))
(defmethod parse-form 'clojure.core/inst? [_ _ _] {:spec inst?, :type :date})
(defmethod parse-form 'clojure.core/seqable? [_ _ _] {:spec seqable?})
(defmethod parse-form 'clojure.core/indexed? [_ _ _] {:spec indexed?})
(defmethod parse-form 'clojure.core/map? [_ _ _] {:spec map?})
(defmethod parse-form 'clojure.core/vector? [_ _ _] {:spec vector?})
(defmethod parse-form 'clojure.core/list? [_ _ _] {:spec list?})
(defmethod parse-form 'clojure.core/seq? [_ _ _] {:spec seq?})
(defmethod parse-form 'clojure.core/char? [_ _ _] {:spec char?})
(defmethod parse-form 'clojure.core/set? [_ _ _] {:spec set?})
(defmethod parse-form 'clojure.core/nil? [_ _ _] {:spec nil?})
(defmethod parse-form 'clojure.core/false? [_ _ _] {:spec false?, :type :boolean})
(defmethod parse-form 'clojure.core/true? [_ _ _] {:spec true?, :type :boolean})
(defmethod parse-form 'clojure.core/zero? [_ _ _] {:spec zero?, :type :long})
#?(:clj (defmethod parse-form 'clojure.core/rational? [_ _ _] {:spec rational?, :type :long}))
(defmethod parse-form 'clojure.core/coll? [_ _ _] {:spec coll?})
(defmethod parse-form 'clojure.core/empty? [_ _ _] {:spec empty?})
(defmethod parse-form 'clojure.core/associative? [_ _ _] {:spec associative?, :type nil})
(defmethod parse-form 'clojure.core/sequential? [_ _ _] {:spec sequential?})
#?(:clj (defmethod parse-form 'clojure.core/ratio? [_ _ _] {:spec ratio?, :type :ratio}))
#?(:clj (defmethod parse-form 'clojure.core/bytes? [_ _ _] {:spec bytes?}))

(defmethod parse-form :clojure.spec.alpha/unknown [_ _ _])

(defmethod parse-form 'clojure.spec.alpha/keys [_ form _]
  (let [{:keys [req opt req-un opt-un key->spec]} (impl/parse-keys form)]
    (cond-> {:type :map
             ::key->spec key->spec
             ::keys (set (concat req opt req-un opt-un))}
      (or req req-un) (assoc ::keys-req (set (concat req req-un)))
      (or opt opt-un) (assoc ::keys-opt (set (concat opt opt-un))))))

#?(:clj
   (defn get-multi-spec-sub-specs
     "Given a multi-spec form, call its multi method methods to retrieve
   its subspecs."
     [multi-spec-form]
     (let [[_ multi-method-symbol & _] multi-spec-form]
       (->> (resolve multi-method-symbol)
            deref
            methods
            (map (fn [[spec-k method]]
                   [spec-k (method nil)])))))
   :cljs
   (defn get-multi-spec-sub-specs
     "Given a multi-spec form, call its multi method methods to retrieve
   its subspecs."
     [multi-spec-form]
     (let [[_ multi-method-symbol & _ :as form] multi-spec-form]
       (when-let [spec (first (filter (fn [v] (= form (s/form v))) (vals (s/registry))))]
         (->> (.-mmvar spec)
              deref
              methods
              (map (fn [[spec-k method]]
                     [spec-k (method nil)])))))))

(defmethod parse-form 'clojure.spec.alpha/multi-spec [_ form _]
  {:type :multi-spec
   ::key (last form)
   ::dispatch (into {} (get-multi-spec-sub-specs form))})

(defmethod parse-form 'clojure.spec.alpha/or [_ form options]
  (let [specs (mapv (comp #(parse-spec-with-spec-ref % options) second) (partition 2 (rest form)))]
    {:type [:or (->> specs (map :type) (distinct) (keep identity) (vec))]
     ::items specs}))

(defmethod parse-form 'clojure.spec.alpha/and [_ form options]
  (let [specs (mapv #(parse-spec-with-spec-ref % options) (rest form))
        types (->> specs (map :type) (distinct) (keep identity) (vec))]
    {:type [:and types]
     ::items specs}))

(defmethod parse-form 'clojure.spec.alpha/merge [_ form options]
  (let [type-priority #(if (= (:type %) :multi-spec) 1 0)]
    (apply impl/deep-merge (->> (rest form)
                                (map #(parse-spec % options))
                                (sort-by type-priority)))))

(defmethod parse-form 'clojure.spec.alpha/every [_ form options]
  (let [{:keys [into]} (apply hash-map (drop 2 form))]
    {::item (parse-spec (second form) options)
     :type
     (cond
       (map? into) :map-of
       (set? into) :set
       :else :vector)}))

; every-ks

(defmethod parse-form 'clojure.spec.alpha/coll-of [_ form options]
  (let [{:keys [into]} (apply hash-map (drop 2 form))]
    {::item (parse-spec-with-spec-ref (second form) options)
     :type
     (cond
       (map? into) :map-of
       (set? into) :set
       :else :vector)}))

(defmethod parse-form 'clojure.spec.alpha/map-of [_ [_ k v] options]
  {:type :map-of
   ::key (parse-spec-with-spec-ref k options)
   ::value (parse-spec-with-spec-ref v options)})

(defmethod parse-form 'spec-tools.core/spec [_ form options]
  (let [parsed (-> form last :spec (parse-spec options))]
    (if (:type parsed) parsed {:type :spec})))

; *
; +
; ?
; alt
; cat
; &
; keys*

(defmethod parse-form 'clojure.spec.alpha/tuple [_ [_ & values] options]
  (let [specs (mapv #(parse-spec-with-spec-ref % options) values)
        types (mapv :type specs)]
    {:type [:tuple types]
     ::items specs}))

(defmethod parse-form 'clojure.spec.alpha/nilable [_ form options]
  (let [spec (-> form second (parse-spec-with-spec-ref options))]
    {:type :nilable
     ::item spec}))

(defmethod parse-form 'spec-tools.core/merge [_ form options]
  (let [type-priority #((:type %) {:map 1
                                   :multi-spec 0})]
    (apply impl/deep-merge (->> (rest form)
                                (map #(parse-spec % options))
                                (sort-by type-priority)))))
