package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"
)

type child struct {
	cmd  *exec.Cmd
	done chan struct{}
}
type upstream struct {
	mu        sync.Mutex
	current   *child
	resetting bool
	directory string
	client    *http.Client
}

func newUpstream(directory string) *upstream {
	return &upstream{directory: directory, client: &http.Client{Timeout: time.Second, Transport: &http.Transport{Proxy: nil},
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}}
}

func childEnvironment(username, password string) []string {
	env := []string{}
	for _, item := range os.Environ() {
		key := strings.SplitN(item, "=", 2)[0]
		if strings.HasPrefix(key, "AGENT2API_ADMIN_") || key == "AGENT2API_PROXY_PORT" || key == "AGENT2API_HOST" ||
			key == "AGENT2API_ALLOW_NO_KEY" || key == "AGENT2API_SELF_UPDATE" || key == "AGENT2API_VERBOSE" {
			continue
		}
		env = append(env, item)
	}
	env = append(env, "AGENT2API_HOST=127.0.0.1", "AGENT2API_PROXY_PORT=3066", "AGENT2API_ALLOW_NO_KEY=0", "AGENT2API_SELF_UPDATE=0", "AGENT2API_VERBOSE=0")
	if username != "" {
		env = append(env, "AGENT2API_ADMIN_USER="+username, "AGENT2API_ADMIN_PASSWORD="+password)
	}
	return env
}

func (u *upstream) start(username, password string) error {
	if conn, err := net.DialTimeout("tcp", "127.0.0.1:3066", 200*time.Millisecond); err == nil {
		conn.Close()
		return errors.New("private upstream port is occupied")
	}
	cmd := exec.Command(filepath.Join(u.directory, "agent2api-server"))
	cmd.Dir = u.directory
	cmd.Env = childEnvironment(username, password)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = nil
	if err := cmd.Start(); err != nil {
		return errors.New("cannot start upstream ARM64 server")
	}
	c := &child{cmd: cmd, done: make(chan struct{})}
	u.current = c
	go func() { _ = cmd.Wait(); close(c.done) }()
	deadline := time.Now().Add(15 * time.Second)
	for time.Now().Before(deadline) {
		select {
		case <-c.done:
			return errors.New("upstream server exited")
		default:
		}
		response, err := u.client.Get("http://127.0.0.1:3066/health")
		if err == nil {
			io.Copy(io.Discard, io.LimitReader(response.Body, 4096))
			response.Body.Close()
			if response.StatusCode == 200 {
				return nil
			}
		}
		time.Sleep(150 * time.Millisecond)
	}
	_ = u.stop()
	return errors.New("upstream readiness timed out")
}

func (u *upstream) stop() error {
	c := u.current
	if c == nil {
		return nil
	}
	select {
	case <-c.done:
		u.current = nil
		return nil
	default:
	}
	_ = c.cmd.Process.Signal(syscall.SIGTERM)
	select {
	case <-c.done:
		u.current = nil
		return nil
	case <-time.After(3 * time.Second):
	}
	_ = c.cmd.Process.Kill()
	select {
	case <-c.done:
		u.current = nil
		return nil
	case <-time.After(2 * time.Second):
		return errors.New("upstream teardown failed")
	}
}

func (u *upstream) exited() bool {
	u.mu.Lock()
	defer u.mu.Unlock()
	if u.resetting {
		return false
	}
	if u.current == nil {
		return true
	}
	select {
	case <-u.current.done:
		return true
	default:
		return false
	}
}

func (u *upstream) resetAdmin(username, password string) error {
	u.mu.Lock()
	defer u.mu.Unlock()
	u.resetting = true
	defer func() { u.resetting = false }()
	if err := u.stop(); err != nil {
		return err
	}
	if err := u.start(username, password); err != nil {
		return err
	}
	// The upstream's official environment override persists a bcrypt hash in SQLite.
	// Restart again without the password so no long-lived child environment retains plaintext.
	if err := u.stop(); err != nil {
		return err
	}
	return u.start("", "")
}

func (u *upstream) read(path string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	request, _ := http.NewRequestWithContext(ctx, http.MethodGet, "http://127.0.0.1:3066"+path, nil)
	response, err := u.client.Do(request)
	if err != nil {
		return nil, errors.New("upstream unavailable")
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return nil, fmt.Errorf("upstream status %d", response.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, 32769))
	if err != nil || len(data) > 32768 {
		return nil, errors.New("upstream response exceeds limit")
	}
	return data, nil
}
