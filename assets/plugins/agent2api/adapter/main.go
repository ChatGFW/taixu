package main

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"
)

func run() error {
	p, err := bootstrap(os.Stdin)
	if err != nil {
		return err
	}
	executable, err := os.Executable()
	if err != nil {
		return errors.New("adapter location unavailable")
	}
	directory := filepath.Dir(executable)
	listener, err := net.Listen("tcp", "127.0.0.1:3065")
	if err != nil {
		return errors.New("gateway port 3065 is occupied")
	}
	defer listener.Close()
	upstream := newUpstream(directory)
	if err = upstream.start("", ""); err != nil {
		return err
	}
	defer func() { upstream.mu.Lock(); defer upstream.mu.Unlock(); _ = upstream.stop() }()
	app := newApplication(p, upstream, filepath.Join(directory, "..", "web"))
	server := &http.Server{Handler: app, ReadHeaderTimeout: 2 * time.Second, IdleTimeout: 10 * time.Second, MaxHeaderBytes: 8192}
	serving := make(chan error, 1)
	go func() { serving <- server.Serve(listener) }()
	defer server.Close()
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGTERM, os.Interrupt)
	defer signal.Stop(signals)
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	fmt.Println("[TaiXu] Agent2API gateway ready; native management page available; no default administrator password")
	for {
		select {
		case <-signals:
			ctx, cancel := context.WithTimeout(context.Background(), time.Second)
			defer cancel()
			_ = server.Shutdown(ctx)
			return nil
		case err := <-serving:
			if err == http.ErrServerClosed {
				return nil
			}
			return errors.New("adapter HTTP server stopped")
		case <-ticker.C:
			if upstream.exited() {
				return errors.New("upstream stopped; handing recovery to TaiXu")
			}
		}
	}
}

func main() {
	if len(os.Args) == 2 && os.Args[1] == "--version" {
		fmt.Println("TaiXu Agent2API adapter 3.0.9 / upstream 3.0.8")
		return
	}
	if len(os.Args) != 1 {
		fmt.Fprintln(os.Stderr, "Unsupported adapter argument")
		os.Exit(2)
	}
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "[TaiXu]", err)
		os.Exit(1)
	}
}
