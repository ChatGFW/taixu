package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func testProtocol() *protocol {
	return &protocol{key: []byte(strings.Repeat("k", 32)), seen: map[string]int64{}, now: func() time.Time { return time.Unix(1793000000, 0) }}
}
func signed(p *protocol, method, path, body, nonce string) *http.Request {
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	r.Header.Set(authHeader, "1793000000:"+nonce+":"+p.sign("request\n1793000000\n"+nonce+"\n"+method+"\n"+path+"\n"+digest([]byte(body))))
	if method == http.MethodPost {
		r.Header.Set("X-Taixu-Plugin-Write", "1")
	}
	return r
}
func TestProtocolBindsMethodPathBodyAndRejectsReplay(t *testing.T) {
	p := testProtocol()
	nonce := strings.Repeat("a", 32)
	r := signed(p, "POST", "/taixu/api/admin/reset", "{}", nonce)
	if _, ok := p.verify(r, []byte("{\"changed\":true}")); ok {
		t.Fatal("changed body accepted")
	}
	if got, ok := p.verify(r, []byte("{}")); !ok || got != nonce {
		t.Fatal("valid request rejected")
	}
	if _, ok := p.verify(r, []byte("{}")); ok {
		t.Fatal("replay accepted")
	}
	r = signed(p, "POST", "/taixu/api/admin/reset", "{}", strings.Repeat("b", 32))
	r.URL.Path = "/taixu/api/status"
	if _, ok := p.verify(r, []byte("{}")); ok {
		t.Fatal("changed path accepted")
	}
}
func TestBootstrapRejectsMissingAndUnknownFields(t *testing.T) {
	key := strings.Repeat("ab", 32)
	valid := "{\"protocol\":\"taixu-service-v1\",\"key\":\"" + key + "\"}\n"
	if _, err := bootstrap(strings.NewReader(valid)); err != nil {
		t.Fatal(err)
	}
	for _, input := range []string{strings.TrimSpace(valid), strings.Replace(valid, "}\n", ",\"extra\":1}\n", 1), strings.Repeat("x", 300) + "\n"} {
		if _, err := bootstrap(strings.NewReader(input)); err == nil {
			t.Fatal("invalid bootstrap accepted")
		}
	}
}
func TestAdminResetRequiresNativeWriteAndKeepsSecretsOutOfRequest(t *testing.T) {
	p := testProtocol()
	a := newApplication(p, newUpstream(t.TempDir()), t.TempDir())
	completed := make(chan struct{})
	a.reset = func(username, password string) error {
		if username != "admin" || len(password) != 32 {
			t.Error("invalid generated credentials")
		}
		close(completed)
		return nil
	}
	body := `{"username":"admin","preserveAccounts":true}`
	unauth := httptest.NewRecorder()
	a.ServeHTTP(unauth, httptest.NewRequest("POST", "/taixu/api/admin/reset", strings.NewReader(body)))
	if unauth.Code != 403 {
		t.Fatal("browser write accepted")
	}
	request := signed(p, "POST", "/taixu/api/admin/reset", body, strings.Repeat("c", 32))
	request.Header.Set("Origin", "https://example.com")
	denied := httptest.NewRecorder()
	a.ServeHTTP(denied, request)
	if denied.Code != 403 {
		t.Fatal("cross-origin write accepted")
	}
	result := httptest.NewRecorder()
	a.ServeHTTP(result, signed(p, "POST", "/taixu/api/admin/reset", body, strings.Repeat("d", 32)))
	var started map[string]string
	if json.Unmarshal(result.Body.Bytes(), &started) != nil || !jobPattern.MatchString(started["job"]) {
		t.Fatal(result.Body.String())
	}
	if result.Header().Get(responseHeader) == "" {
		t.Fatal("unsigned native response")
	}
	<-completed
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		a.jobsMu.Lock()
		job := *a.jobs[started["job"]]
		a.jobsMu.Unlock()
		if job.State == "done" {
			if len(job.Password) != 32 {
				t.Fatal("missing password")
			}
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatal("reset did not finish")
}
func TestResetRejectsUnknownFieldsAndDestructivePayload(t *testing.T) {
	p := testProtocol()
	a := newApplication(p, newUpstream(t.TempDir()), t.TempDir())
	a.reset = func(string, string) error { t.Error("invalid operation executed"); return nil }
	// Hex nonces keep every case past signature verification, so only the payload rule can reject it.
	nonces := []string{"e0000000000000000000000000000000", "e1000000000000000000000000000000", "e2000000000000000000000000000000"}
	for i, body := range []string{`{"username":"admin","preserveAccounts":false}`, `{"username":"admin","preserveAccounts":true,"deleteData":true}`, `{"username":"x;rm","preserveAccounts":true}`} {
		result := httptest.NewRecorder()
		a.ServeHTTP(result, signed(p, "POST", "/taixu/api/admin/reset", body, nonces[i]))
		if !strings.Contains(result.Body.String(), "error") {
			t.Fatalf("invalid payload accepted: %s", body)
		}
	}
}
func TestChildEnvironmentOverridesUnsafeSettingsAndRemovesOldAdministrator(t *testing.T) {
	t.Setenv("AGENT2API_HOST", "0.0.0.0")
	t.Setenv("AGENT2API_ALLOW_NO_KEY", "1")
	t.Setenv("AGENT2API_ADMIN_PASSWORD", "old-secret")
	value := strings.Join(childEnvironment("", ""), "\n")
	if strings.Contains(value, "old-secret") || strings.Contains(value, "AGENT2API_ALLOW_NO_KEY=1") || !strings.Contains(value, "AGENT2API_HOST=127.0.0.1") {
		t.Fatal("unsafe inherited environment")
	}
}
