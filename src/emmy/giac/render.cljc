#_"SPDX-License-Identifier: GPL-3.0"

(ns emmy.giac.render
  "Renders Emmy expressions in the input syntax of the Giac computer algebra
  system, the way [[emmy.expression.render/->TeX]] renders them as TeX."
  (:require [clojure.string :as s]
            [emmy.expression.render]
            [emmy.value :as v]))

;; The infix renderer builder is private to emmy.expression.render; ->giac is
;; one more specialization of it, like ->infix, ->TeX and ->JavaScript.
(def ^:private make-infix-renderer
  @#'emmy.expression.render/make-infix-renderer)

(def reserved-symbols
  "Symbols that Giac reads as constants: `i` is the imaginary unit and `e` is
  Euler's number. [[->giac]] writes them with [[symbol-prefix]] so that an Emmy
  variable named `i` or `e` stays a variable; [[emmy.giac]] removes the prefix
  on the way back."
  '#{i e})

(def symbol-prefix "emmy_")

(defn- render-primitive [x]
  (cond
    (= x ##Inf) "+infinity"
    (= x ##-Inf) "-infinity"
    (reserved-symbols x) (str symbol-prefix x)
    :else x))

(defn- render-ratio
  "Renders `(/ n d)` with integer `n` and `d` as `n/d`, and `(/ d)` as `1/d`;
  returns nil otherwise so the generic infix rule applies."
  [[num denom :as xs]]
  (case (count xs)
    1 (when (v/integral? num) (str "1/" num))
    2 (when (and (v/integral? num) (v/integral? denom)) (str num "/" denom))
    nil))

(def ->giac
  "Converts an S-expression to Giac input, as a string.

  Up and down tuples become Giac lists, `log` becomes `ln` and the two-argument
  `atan` becomes `atan2`. Symbols in [[reserved-symbols]] are prefixed so Giac
  does not read them as constants."
  (let [giac-list #(str \[ (s/join ", " %) \])]
    (make-infix-renderer
     :precedence-map '{expt 8,
                       :apply 7,
                       u- 6,
                       * 5, / 5,
                       + 4, - 4,
                       = 3, > 3, < 3, >= 3, <= 3,
                       not 2, and 2, or 1}
     :infix? '#{* + - / u- expt = > < >= <=}
     :juxtapose-multiply "*"
     :rename-functions {'log "ln"
                        'ceiling "ceil"
                        'integer-part "trunc"
                        'fractional-part "frac"
                        'remainder "irem"}
     :render-primitive render-primitive
     :special-handlers {'up giac-list
                        'down giac-list
                        'atan (fn [args]
                                (str (if (next args) "atan2" "atan")
                                     "(" (s/join ", " args) ")"))
                        'and (fn [xs] (s/join " and " xs))
                        'or (fn [xs] (s/join " or " xs))
                        '/ render-ratio})))
