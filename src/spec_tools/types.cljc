(ns spec-tools.types)

(defmulti resolve-type identity :default ::default)

(defmethod resolve-type ::default [x]
  (println "DEFAULT: " x) nil)

(defn resolve-type-or-fail [x]
  (or (resolve-type x)
      (throw
        (ex-info
          (str
            "Can't resolve type of a spec: '" x "'. "
            "You need to provide a `:spec/type` for the spec or "
            "add a dispatch function for `spec-tools.types/resolve-type`.")
          {:spec x}))))

; any? (one-of [(return nil) (any-printable)])
; some? (such-that some? (any-printable))
; number? (one-of [(large-integer) (double)])

; integer? (large-integer)
(defmethod resolve-type 'clojure.core/integer? [_] :long)

; int? (large-integer)
(defmethod resolve-type 'clojure.core/int? [_] :long)

; pos-int? (large-integer* {:min 1})
(defmethod resolve-type 'clojure.core/pos-int? [_] :long)

; neg-int? (large-integer* {:max -1})
(defmethod resolve-type 'clojure.core/neg-int? [_] :long)

; nat-int? (large-integer* {:min 0})
(defmethod resolve-type 'clojure.core/nat-int? [_] :long)

; float? (double)
(defmethod resolve-type 'clojure.core/float? [_] :double)

; double? (double)
(defmethod resolve-type 'clojure.core/double? [_] :double)

; boolean? (boolean)
(defmethod resolve-type 'clojure.core/boolean? [_] :boolean)

; string? (string-alphanumeric)
(defmethod resolve-type 'clojure.core/string? [_] :string)

; ident? (one-of [(keyword-ns) (symbol-ns)])
(defmethod resolve-type 'clojure.core/ident? [_] :keyword)

; simple-ident? (one-of [(keyword) (symbol)])
(defmethod resolve-type 'clojure.core/simple-ident? [_] :keyword)

; qualified-ident? (such-that qualified? (one-of [(keyword-ns) (symbol-ns)]))
(defmethod resolve-type 'clojure.core/qualified-ident? [_] :keyword)

; keyword? (keyword-ns)
(defmethod resolve-type 'clojure.core/keyword? [_] :keyword)

; simple-keyword? (keyword)
(defmethod resolve-type 'clojure.core/simple-keyword? [_] :keyword)

; qualified-keyword? (such-that qualified? (keyword-ns))
(defmethod resolve-type 'clojure.core/qualified-keyword? [_] :keyword)

; symbol? (symbol-ns)
(defmethod resolve-type 'clojure.core/symbol? [_] :symbol)

; simple-symbol? (symbol)
(defmethod resolve-type 'clojure.core/simple-symbol? [_] :symbol)

; qualified-symbol? (such-that qualified? (symbol-ns))
(defmethod resolve-type 'clojure.core/qualified-symbol? [_] :symbol)

; uuid? (uuid)
(defmethod resolve-type 'clojure.core/uuid? [_] :uuid)

; uri? (fmap #(java.net.URI/create (str "http://" % ".com")) (uuid))
(defmethod resolve-type 'clojure.core/uri? [_] :uri)

; bigdec? (fmap #(BigDecimal/valueOf %)
;               (double* {:infinite? false :NaN? false}))
(defmethod resolve-type 'clojure.core/bigdec? [_] :bigdec)

; inst? (fmap #(java.util.Date. %)
;             (large-integer))
(defmethod resolve-type 'clojure.core/inst? [_] :date)

; seqable? (one-of [(return nil)
;                   (list simple)
;                   (vector simple)
;                   (map simple simple)
;                   (set simple)
;                   (string-alphanumeric)])
; indexed? (vector simple)
; map? (map simple simple)
; vector? (vector simple)
; list? (list simple)
; seq? (list simple)
; char? (char)
; set? (set simple)

; nil? (return nil)
(defmethod resolve-type 'clojure.core/nil? [_] :nil)

; false? (return false)
(defmethod resolve-type 'clojure.core/false? [_] :boolean)

; true? (return true)
(defmethod resolve-type 'clojure.core/true? [_] :boolean)

; zero? (return 0)
(defmethod resolve-type 'clojure.core/zero? [_] :long)

; rational? (one-of [(large-integer) (ratio)])
(defmethod resolve-type 'clojure.core/rational? [_] :long)

; coll? (one-of [(map simple simple)
;                (list simple)
;                (vector simple)
;                (set simple)])
; empty? (elements [nil '() [] {} #{}])
; associative? (one-of [(map simple simple) (vector simple)])
; sequential? (one-of [(list simple) (vector simple)])
; ratio? (such-that ratio? (ratio))
(defmethod resolve-type 'clojure.core/ratio? [_] :ratio)

; bytes? (bytes)
