// carlos-setup is a terminal UI wrapper around docker/setup.sh.
//
// This is a SKELETON - the full Bubbletea implementation is planned for a
// follow-up PR. Today, this binary simply delegates to setup.sh so that the
// CLI invocation story is consistent regardless of whether the user has the
// Go TUI installed or is reading the shell script directly.
//
// Build:
//
//	cd docker/carlos-setup
//	go build -o carlos-setup
//
// Cross-compile for distribution via GitHub Releases:
//
//	GOOS=linux   GOARCH=amd64 go build -o dist/carlos-setup-linux-amd64
//	GOOS=linux   GOARCH=arm64 go build -o dist/carlos-setup-linux-arm64
//	GOOS=darwin  GOARCH=amd64 go build -o dist/carlos-setup-darwin-amd64
//	GOOS=darwin  GOARCH=arm64 go build -o dist/carlos-setup-darwin-arm64
//	GOOS=windows GOARCH=amd64 go build -o dist/carlos-setup-windows-amd64.exe
//
// See README.md for the planned TUI architecture.
//
// License: GPL-2.0+
package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
)

const version = "0.1.0-skeleton"

func main() {
	// --version short-circuit for scripts that just want to probe installation.
	for _, arg := range os.Args[1:] {
		if arg == "--version" || arg == "-v" {
			fmt.Printf("carlos-setup %s\n", version)
			return
		}
	}

	fmt.Println("carlos-setup (skeleton)")
	fmt.Println("=======================")
	fmt.Println()
	fmt.Println("The Bubbletea TUI is planned for a follow-up PR.")
	fmt.Println("For now, invoking the bash installer which provides the same")
	fmt.Println("functionality with full transparency (every command is logged).")
	fmt.Println()

	if runtime.GOOS == "windows" {
		fmt.Fprintln(os.Stderr, "Windows support requires WSL2 or Git Bash.")
		fmt.Fprintln(os.Stderr, "Run setup.sh from a bash shell instead.")
		os.Exit(1)
	}

	// Locate setup.sh. We expect it at ../setup.sh relative to this binary
	// when invoked from a source checkout, or at an absolute path when
	// installed system-wide.
	exe, err := os.Executable()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: cannot determine binary location: %v\n", err)
		os.Exit(1)
	}
	exeDir := filepath.Dir(exe)

	candidates := []string{
		filepath.Join(exeDir, "..", "setup.sh"),
		filepath.Join(exeDir, "setup.sh"),
		"/usr/local/share/carlos-emr/setup.sh",
	}

	var script string
	for _, c := range candidates {
		if _, err := os.Stat(c); err == nil {
			script = c
			break
		}
	}

	if script == "" {
		fmt.Fprintln(os.Stderr, "Error: setup.sh not found. Searched:")
		for _, c := range candidates {
			fmt.Fprintf(os.Stderr, "  %s\n", c)
		}
		fmt.Fprintln(os.Stderr, "Clone the carlos-emr/carlos repo and run docker/setup.sh directly.")
		os.Exit(1)
	}

	// Forward all arguments. setup.sh handles its own flag parsing.
	cmd := exec.Command("bash", append([]string{script}, os.Args[1:]...)...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			os.Exit(exitErr.ExitCode())
		}
		fmt.Fprintf(os.Stderr, "Error running setup.sh: %v\n", err)
		os.Exit(1)
	}
}

// TODO: Full TUI implementation below. Planned screens:
//
//   1. Welcome screen (lipgloss banner, brief description)
//   2. Prerequisites checklist (auto-detect podman/docker, show pass/fail)
//   3. Configuration form (province, password, ports, demo toggle, WAR source)
//   4. Review screen (display all commands that will run - transparency req)
//   5. Deploy progress (spinner per step, live log tail)
//   6. Complete screen (access URL, credentials, next steps)
//
// The TUI should wrap setup.sh logic by calling setup.sh in --non-interactive
// mode with environment variables set from form input. This preserves the
// single-source-of-truth principle: setup.sh is authoritative, TUI is a
// front-end.
