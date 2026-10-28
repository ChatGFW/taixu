package main

import (
	"bufio"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

const authHeader = "X-Taixu-Service-Authorization"
const responseHeader = "X-Taixu-Service-Response"

var headerPattern = regexp.MustCompile(`^[0-9]{10,12}:[a-f0-9]{32}:[a-f0-9]{64}$`)

type protocol struct {
	key  []byte
	mu   sync.Mutex
	seen map[string]int64
	now  func() time.Time
}

func bootstrap(reader io.Reader) (*protocol, error) {
	line, err := bufio.NewReaderSize(reader, 256).ReadSlice('\n')
	if err != nil || len(line) > 255 {
		return nil, errors.New("missing native service bootstrap")
	}
	var value struct {
		Protocol string `json:"protocol"`
		Key      string `json:"key"`
	}
	decoder := json.NewDecoder(strings.NewReader(string(line)))
	decoder.DisallowUnknownFields()
	if decoder.Decode(&value) != nil || value.Protocol != "taixu-service-v1" || len(value.Key) != 64 {
		return nil, errors.New("invalid native bootstrap")
	}
	key, err := hex.DecodeString(value.Key)
	if err != nil || strings.ToLower(value.Key) != value.Key {
		return nil, errors.New("invalid service key")
	}
	return &protocol{key: key, seen: map[string]int64{}, now: time.Now}, nil
}

func digest(body []byte) string { sum := sha256.Sum256(body); return hex.EncodeToString(sum[:]) }
func (p *protocol) sign(text string) string {
	mac := hmac.New(sha256.New, p.key)
	mac.Write([]byte(text))
	return hex.EncodeToString(mac.Sum(nil))
}
func (p *protocol) verify(r *http.Request, body []byte) (string, bool) {
	headers := r.Header.Values(authHeader)
	if len(headers) != 1 || !headerPattern.MatchString(headers[0]) || r.URL.RawQuery != "" {
		return "", false
	}
	parts := strings.Split(headers[0], ":")
	timestamp, _ := strconv.ParseInt(parts[0], 10, 64)
	now := p.now().Unix()
	if timestamp < now-30 || timestamp > now+30 {
		return "", false
	}
	expected := p.sign("request\n" + parts[0] + "\n" + parts[1] + "\n" + r.Method + "\n" + r.URL.EscapedPath() + "\n" + digest(body))
	if !hmac.Equal([]byte(expected), []byte(parts[2])) {
		return "", false
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	for nonce, expiry := range p.seen {
		if expiry <= now {
			delete(p.seen, nonce)
		}
	}
	if _, exists := p.seen[parts[1]]; exists || len(p.seen) >= 4096 {
		return "", false
	}
	p.seen[parts[1]] = timestamp + 31
	return parts[1], true
}

func (p *protocol) reply(w http.ResponseWriter, nonce string, status int, body []byte, kind string) {
	w.Header().Set("Content-Type", kind)
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	if nonce != "" {
		w.Header().Set(responseHeader, p.sign("response\n"+nonce+"\n"+strconv.Itoa(status)+"\n"+digest(body)))
	}
	w.WriteHeader(status)
	_, _ = w.Write(body)
}
func (p *protocol) json(w http.ResponseWriter, nonce string, value any) {
	body, _ := json.Marshal(value)
	p.reply(w, nonce, 200, body, "application/json; charset=utf-8")
}
