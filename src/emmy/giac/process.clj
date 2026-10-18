#_"SPDX-License-Identifier: GPL-3.0"

(ns emmy.giac.process
  "A JVM engine for [[emmy.giac]]: one long-lived WASI runtime process running
  `giac-cli.wasm` in server mode. Commands go in one per line; each result comes
  back framed by STX and ETX, so Giac's own log lines are skipped. The process
  keeps one Giac session, so settings and definitions persist between calls."
  (:require [clojure.string :as s]
            [emmy.giac :as giac])
  (:import (java.io BufferedReader InputStreamReader OutputStreamWriter Writer)
           (java.lang Process ProcessBuilder)))

(def default-wasm
  "Where `wasm/giac/build.sh` puts the server module, unless `EMMY_GIAC_WASM`
  names another file."
  (or (System/getenv "EMMY_GIAC_WASM")
      (str (System/getProperty "user.home") "/.cache/giac-wasm/out/giac-cli.wasm")))

(def default-runtime
  "The WASI runtime command line, before the module path. zwasm is the runtime
  cljw uses; `--cache` keeps its compiled code between starts."
  ["zwasm" "run" "--cache"])

(defn- read-frame
  "Reads characters up to the next ETX and returns what came after the last STX."
  [^BufferedReader in]
  (let [sb (StringBuilder.)]
    (loop [inside? false]
      (let [c (.read in)]
        (cond
          (neg? c) (throw (ex-info "Giac process closed its output" {}))
          (= c 2) (do (.setLength sb 0) (recur true))
          (= c 3) (str sb)
          :else (do (when inside? (.append sb (char c)))
                    (recur inside?)))))))

(defrecord ProcessEngine [^Process process ^Writer out ^BufferedReader in]
  giac/IGiac
  (-eval [_ command]
    (when (s/includes? command "\n")
      (throw (ex-info "Giac commands must be a single line" {:command command})))
    (locking process
      (.write out (str command "\n"))
      (.flush out)
      (read-frame in))))

(defn start!
  "Starts a Giac engine and installs the `emmy_sexp` printer. Options:

  - `:wasm`     path to `giac-cli.wasm` (default [[default-wasm]])
  - `:runtime`  WASI runtime command line (default [[default-runtime]]); for
                wasmtime use `[\"wasmtime\" \"run\" \"-W\" \"exceptions=y\"]`
  - `:settings` a map applied with [[emmy.giac/configure!]]"
  [& {:keys [wasm runtime settings]
      :or {wasm default-wasm runtime default-runtime}}]
  (let [process (-> (ProcessBuilder. ^java.util.List (conj (vec runtime) wasm))
                    (.redirectErrorStream false)
                    (.start))
        engine (->ProcessEngine
                process
                (OutputStreamWriter. (.getOutputStream process) "UTF-8")
                (BufferedReader. (InputStreamReader. (.getInputStream process) "UTF-8")))]
    (cond-> (giac/install! engine)
      settings (giac/configure! settings))))

(defn stop!
  "Ends the engine's process."
  [^ProcessEngine engine]
  (.close ^Writer (:out engine))
  (.destroy ^Process (:process engine)))
