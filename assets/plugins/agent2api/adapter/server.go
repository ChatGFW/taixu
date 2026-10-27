package main

import (
	"bytes"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

var usernamePattern = regexp.MustCompile(`^[A-Za-z0-9_.-]{3,32}$`)
var jobPattern = regexp.MustCompile(`^[a-f0-9]{32}$`)

type operation struct {
	State    string    `json:"state"`
	Username string    `json:"username,omitempty"`
	Password string    `json:"password,omitempty"`
	Error    string    `json:"error,omitempty"`
	Expires  time.Time `json:"-"`
}
type application struct {
	protocol *protocol
	upstream *upstream
	web      string
	proxy    *httputil.ReverseProxy
	jobsMu   sync.Mutex
	jobs     map[string]*operation
	busy     bool
	reset    func(string, string) error
}

func newApplication(p *protocol, u *upstream, web string) *application {
	target, _ := url.Parse("http://127.0.0.1:3066")
	proxy := httputil.NewSingleHostReverseProxy(target)
	proxy.Transport = &http.Transport{Proxy: nil}
	proxy.FlushInterval = -1
	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		http.Error(w, "Agent2API is starting or unavailable", http.StatusServiceUnavailable)
	}
	return &application{protocol: p, upstream: u, web: web, proxy: proxy, jobs: map[string]*operation{}, reset: u.resetAdmin}
}

func (a *application) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if !strings.HasPrefix(r.URL.Path, "/taixu/") {
		r.Header.Del(authHeader)
		r.Header.Del(responseHeader)
		a.proxy.ServeHTTP(w, r)
		return
	}
	if r.Method != http.MethodGet && r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", 405)
		return
	}
	var body []byte
	if r.Method == http.MethodPost {
		if r.Header.Get("X-Taixu-Plugin-Write") != "1" || len(r.Header.Values("Origin")) != 0 || len(r.TransferEncoding) != 0 || r.ContentLength < 1 || r.ContentLength > 1024 {
			http.Error(w, "Forbidden", 403)
			return
		}
		var err error
		body, err = io.ReadAll(io.LimitReader(r.Body, 1025))
		if err != nil || len(body) > 1024 || !utf8.Valid(body) {
			http.Error(w, "Invalid body", 400)
			return
		}
	} else if r.ContentLength > 0 {
		http.Error(w, "Invalid body", 400)
		return
	}
	nonce, ok := a.protocol.verify(r, body)
	if !ok {
		http.Error(w, "Unauthorized", 403)
		return
	}
	path := strings.TrimPrefix(r.URL.Path, "/taixu/")
	if r.Method == http.MethodGet {
		switch path {
		case "", "index.html", "app.js", "app.css":
			if path == "" {
				path = "index.html"
			}
			data, err := os.ReadFile(filepath.Join(a.web, path))
			if err != nil {
				a.protocol.json(w, nonce, map[string]string{"error": "page assets unavailable"})
				return
			}
			kind := map[string]string{"index.html": "text/html", "app.js": "application/javascript", "app.css": "text/css"}[path]
			a.protocol.reply(w, nonce, 200, data, kind+"; charset=utf-8")
			return
		case "api/status":
			data, err := a.upstream.read("/api/panel/status")
			value := map[string]any{"gateway": "http://127.0.0.1:3065/v1", "version": "3.0.8", "adapter": "3.0.9", "ready": err == nil}
			if err == nil {
				var status map[string]any
				if json.Unmarshal(data, &status) == nil {
					value["panel"] = status["data"]
				}
			}
			a.protocol.json(w, nonce, value)
			return
		case "api/models":
			data, err := a.upstream.read("/v1/models")
			if err != nil {
				a.protocol.json(w, nonce, map[string]string{"error": "model list unavailable"})
				return
			}
			a.protocol.reply(w, nonce, 200, data, "application/json; charset=utf-8")
			return
		}
		if strings.HasPrefix(path, "api/job/") && jobPattern.MatchString(strings.TrimPrefix(path, "api/job/")) {
			a.jobsMu.Lock()
			a.pruneJobs()
			job := a.jobs[strings.TrimPrefix(path, "api/job/")]
			if job == nil {
				a.protocol.json(w, nonce, map[string]string{"error": "operation expired or unknown"})
			} else {
				a.protocol.json(w, nonce, *job)
			}
			a.jobsMu.Unlock()
			return
		}
	} else if path == "api/admin/reset" {
		var value struct {
			Username         string `json:"username"`
			PreserveAccounts bool   `json:"preserveAccounts"`
		}
		decoder := json.NewDecoder(bytes.NewReader(body))
		decoder.DisallowUnknownFields()
		if decoder.Decode(&value) != nil || decoder.Decode(&struct{}{}) != io.EOF || !usernamePattern.MatchString(value.Username) || !value.PreserveAccounts {
			a.protocol.json(w, nonce, map[string]string{"error": "username must be 3–32 letters, numbers, dots, underscores or hyphens; preserveAccounts must be true"})
			return
		}
		a.beginReset(w, nonce, value.Username)
		return
	}
	a.protocol.json(w, nonce, map[string]string{"error": "unsupported operation"})
}

func (a *application) pruneJobs() {
	for key, job := range a.jobs {
		if job.State != "running" && time.Now().After(job.Expires) {
			delete(a.jobs, key)
		}
	}
}
func (a *application) beginReset(w http.ResponseWriter, nonce, username string) {
	idBytes := make([]byte, 16)
	passwordBytes := make([]byte, 24)
	if _, err := rand.Read(idBytes); err != nil {
		a.protocol.json(w, nonce, map[string]string{"error": "secure random unavailable"})
		return
	}
	if _, err := rand.Read(passwordBytes); err != nil {
		a.protocol.json(w, nonce, map[string]string{"error": "secure random unavailable"})
		return
	}
	id := hex.EncodeToString(idBytes)
	password := base64.RawURLEncoding.EncodeToString(passwordBytes)
	a.jobsMu.Lock()
	defer a.jobsMu.Unlock()
	a.pruneJobs()
	if a.busy || len(a.jobs) >= 8 {
		a.protocol.json(w, nonce, map[string]string{"error": "an operation is already running or the temporary result limit was reached"})
		return
	}
	a.busy = true
	a.jobs[id] = &operation{State: "running"}
	go func() {
		err := a.reset(username, password)
		a.jobsMu.Lock()
		defer a.jobsMu.Unlock()
		a.busy = false
		result := &operation{State: "done", Username: username, Password: password, Expires: time.Now().Add(5 * time.Minute)}
		if err != nil {
			result = &operation{State: "failed", Error: "管理员更新未完成；请查看服务状态。账号数据未清除。", Expires: time.Now().Add(5 * time.Minute)}
		}
		a.jobs[id] = result
	}()
	a.protocol.json(w, nonce, map[string]string{"job": id})
}
