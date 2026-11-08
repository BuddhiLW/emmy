#_"SPDX-License-Identifier: GPL-3.0"

(ns emmy.giac
  "Two-way bridge between Emmy and the Giac computer algebra system.

  Emmy expressions go out through [[emmy.giac.render/->giac]]; Giac's answers
  come back as ordinary Emmy expressions. Giac itself runs behind the [[IGiac]]
  protocol, so the same calls work against any engine: a WebAssembly module in
  the browser, a child process on the JVM, or cljw.

  Every engine must have [[install!]] run once: it defines `emmy_sexp` inside
  Giac, a printer that writes any Giac value as EDN. That printer is what makes
  the way back exact, with no infix parsing on the Clojure side.

  ```clojure
  (require '[emmy.giac :as giac]
           '[emmy.giac.process :as gp])

  (def e (gp/start!))
  (giac/integrate e '(* x (exp x) (sin x)) 'x)
  (giac/configure! e {:assume ['(> x 0)]})
  ```"
  (:require #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as edn])
            [clojure.string :as s]
            [emmy.abstract.number :as an]
            [emmy.complex :as c]
            [emmy.generic :as g]
            [emmy.giac.render :as render]))

(defprotocol IGiac
  (-eval [engine command]
    "Evaluates one line of Giac input and returns Giac's printed result."))

;; ## Giac -> EDN printer
;;
;; Installed into each engine by [[install!]]. Each value prints as EDN:
;; integers and floats as numbers, rationals as `("/" n d)`, identifiers as
;; `("id" "name")`, lists as vectors, and every other expression as
;; `("<operator>" args...)`, using Giac's own `sommet` (operator) and `feuille`
;; (arguments).

(def ^:private sexp-program
  (s/join
   " "
   ["emmy_sexp(e):={"
    "local t,s,a,k;"
    "t:=type(e);"
    "if (t==DOM_INT || t==DOM_FLOAT) return string(e);"
    "if (t==DOM_RAT) return \"(\\\"/\\\" \"+string(numer(e))+\" \"+string(denom(e))+\")\";"
    "if (t==DOM_IDENT) return \"(\\\"id\\\" \\\"\"+string(e)+\"\\\")\";"
    "if (t==DOM_COMPLEX) return \"(\\\"complex\\\" \"+emmy_sexp(re(e))+\" \"+emmy_sexp(im(e))+\")\";"
    "if (t==DOM_LIST) {"
    "s:=\"[\";"
    "for k from 0 to size(e)-1 do s:=s+\" \"+emmy_sexp(e[k]); od;"
    "return s+\"]\";"
    "}"
    "if (t==DOM_SYMBOLIC) {"
    "a:=[feuille(e)];"
    "s:=\"(\\\"\"+string(sommet(e))+\"\\\"\";"
    "for k from 0 to size(a)-1 do s:=s+\" \"+emmy_sexp(a[k]); od;"
    "return s+\")\";"
    "}"
    "return \"(\\\"unsupported\\\" \\\"\"+string(t)+\"\\\")\";"
    "}"]))

(defn install!
  "Defines the `emmy_sexp` printer inside `engine`'s Giac session. Engines call
  this when they start; it is safe to call again. Returns `engine`."
  [engine]
  (-eval engine sexp-program)
  engine)

;; ## Settings
;;
;; Giac's session state, written as data. Each key maps to Giac commands that
;; change the state of the session the engine keeps.

(defn settings->commands
  "Returns the Giac commands that apply `settings`, a map with any of:

  - `:assume`   expressions such as `'(> x 0)`, each sent to `assume`
  - `:purge`    symbols whose definitions and assumptions are cleared
  - `:complex?` true to work over the complex numbers, false for the reals
  - `:angle`    `:radian` or `:degree`
  - `:digits`   number of significant digits for floating point results"
  [{:keys [assume purge complex? angle digits]}]
  (concat
   (for [v purge] (str "purge(" (render/->giac v) ")"))
   (when (some? complex?) [(str "complex_mode:=" (if complex? 1 0))])
   (case angle
     :radian ["angle_radian:=1"]
     :degree ["angle_radian:=0"]
     nil)
   (when digits [(str "Digits:=" digits)])
   (for [a assume] (str "assume(" (render/->giac a) ")"))))

(defn configure!
  "Applies `settings` (see [[settings->commands]]) to `engine`'s session.
  Returns `engine`."
  [engine settings]
  (doseq [command (settings->commands settings)]
    (-eval engine command))
  engine)

;; ## Reading Giac results

(defn- strip-wrapping
  "Returns `s` without its first and last character when both equal `ch`,
  else nil."
  [s ch]
  (when (and (>= (count s) 2)
             (s/starts-with? s ch)
             (s/ends-with? s ch))
    (subs s 1 (dec (count s)))))

(defn- unquote-giac-string
  "Giac prints a string result as a literal: wrapped in double quotes, with each
  embedded double quote doubled. Returns the string's contents, or nil when `s`
  is not a string literal (for example a Giac error message)."
  [s]
  (some-> (strip-wrapping (s/trim s) "\"")
          (s/replace "\"\"" "\"")))

(defn- giac-symbol [name]
  (let [prefix render/symbol-prefix]
    (if (s/starts-with? name prefix)
      (symbol (subs name (count prefix)))
      (symbol name))))

(def ^:private relations
  '{"=" =, ">" >, "<" <, ">=" >=, "<=" <=, "!=" not=})

(def ^:private operators
  (let [;; Emmy evaluates (cos 2) to a float; wrapping exact numbers keeps
        ;; Giac's exact answers exact: (cos (literal-number 2)) stays (cos 2).
        exactly (fn [f]
                  (fn [& xs]
                    (apply f (map #(if (and (number? %) (g/exact? %))
                                     (an/literal-number %)
                                     %)
                                  xs))))]
    {"+" g/+, "*" g/*, "-" g/negate, "inv" g/invert,
     "^" (exactly g/expt), "exp" (exactly g/exp), "ln" (exactly g/log),
     "sqrt" (exactly g/sqrt), "abs" g/abs,
     "sin" (exactly g/sin), "cos" (exactly g/cos), "tan" (exactly g/tan),
     "asin" (exactly g/asin), "acos" (exactly g/acos), "atan" (exactly g/atan),
     "sinh" (exactly g/sinh), "cosh" (exactly g/cosh), "tanh" (exactly g/tanh),
     "floor" g/floor, "ceil" g/ceiling}))

(defn ->emmy
  "Converts a value printed by `emmy_sexp` and read as EDN into an Emmy value.

  Known operators go through Emmy's generic arithmetic, so the result behaves
  like any expression Emmy built itself. Applications of unknown functions,
  `f(x)`, become `(f x)`; Giac's derivatives of them, `diff(f,0)(x,y)`, become
  `(((partial 0) f) x y)`; relations become `(= a b)` and so on; any other Giac
  operator is kept by name as a literal expression."
  [form]
  (letfn [(op-of [tag]
            ;; Giac prints operators quoted, as in '+'.
            (or (strip-wrapping tag "'") tag))
          (function-head [f]
            ;; diff(f,k) is the derivative of f in its k-th argument (from 0).
            (if (and (seq? f) (= "diff" (op-of (first f))) (= 3 (count f)))
              (let [[_ g k] f]
                (list (list 'partial k) (function-head g)))
              (g/freeze (->emmy f))))]
    (cond
      (number? form) form
      (vector? form) (mapv ->emmy form)
      (seq? form)
      (let [[tag & args] form
            op (op-of tag)]
        (case op
          "id" (let [name (first args)]
                 (case name
                   "infinity" ##Inf
                   (giac-symbol name)))
          "/" (g/div (first args) (second args))
          "complex" (let [[re im] (map ->emmy args)]
                      (if (and (number? re) (number? im))
                        (c/complex re im)
                        (g/+ re (g/* im c/I))))
          "of" (let [[f fargs] args
                     fargs (if (vector? fargs) fargs [fargs])]
                 (an/literal-number
                  (cons (function-head f)
                        (map (comp g/freeze ->emmy) fargs))))
          "unsupported" (throw (ex-info (str "Giac value of type " (first args)
                                             " has no Emmy counterpart")
                                        {:giac-type (first args)}))
          (let [vals (map ->emmy args)]
            (if-let [f (operators op)]
              (apply f vals)
              (an/literal-number
               (cons (or (relations op) (symbol op))
                     (map g/freeze vals)))))))
      :else (throw (ex-info "Unexpected form from emmy_sexp" {:form form})))))

(defn read-result
  "Converts one result of `emmy_sexp(...)`, exactly as Giac printed it, into an
  Emmy value. Throws with Giac's message when Giac answered with an error
  instead."
  [printed]
  (if-let [edn-string (unquote-giac-string printed)]
    (->emmy (edn/read-string edn-string))
    (throw (ex-info (str "Giac: " (s/trim printed)) {:giac printed}))))

;; ## Calling Giac

(defn call
  "Applies the Giac command `op` (a symbol or string) to `args` in `engine` and
  returns the result as an Emmy value. Each argument is rendered with
  [[emmy.giac.render/->giac]], so Emmy expressions, symbols, numbers and forms
  such as `'(= x 0)` all work:

  ```clojure
  (call engine 'limit '(/ (sin x) x) 'x 0)
  ```"
  [engine op & args]
  (read-result
   (-eval engine (str "emmy_sexp(" op "("
                      (s/join "," (map render/->giac args))
                      "))"))))

(defn simplify [engine expr] (call engine 'simplify expr))
(defn factor [engine expr] (call engine 'factor expr))
(defn expand [engine expr] (call engine 'expand expr))

(defn integrate
  "Antiderivative of `expr` in `x`, or the definite integral from `a` to `b`."
  ([engine expr x] (call engine 'integrate expr x))
  ([engine expr x a b] (call engine 'integrate expr x a b)))

(defn limit
  "Limit of `expr` as `x` tends to `x0`."
  [engine expr x x0]
  (call engine 'limit expr x x0))

(defn solve
  "Solutions of `eqn` (an expression equal to zero, or a form like `'(= a b)`)
  for `x`, as a vector."
  [engine eqn x]
  (call engine 'solve eqn x))

(defn series
  "Taylor series of `expr` in `x` around `x0`, to order `n`. Giac marks the
  truncation with an `order_size` term."
  [engine expr x x0 n]
  (call engine 'series expr x x0 n))

(defn latex
  "Giac's own LaTeX rendering of `expr`, after Giac evaluates it. Compare
  [[emmy.expression.render/->TeX]], which renders Emmy's form of the same
  expression."
  [engine expr]
  (let [printed (-eval engine (str "latex(" (render/->giac expr) ")"))]
    (or (unquote-giac-string printed)
        (throw (ex-info (str "Giac: " (s/trim printed)) {:giac printed})))))
