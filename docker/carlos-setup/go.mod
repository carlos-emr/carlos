module github.com/carlos-emr/carlos/docker/carlos-setup

go 1.22

// TUI dependencies are intentionally not committed yet. When the TUI is
// implemented in the follow-up PR, run:
//
//   cd docker/carlos-setup
//   go get github.com/charmbracelet/bubbletea
//   go get github.com/charmbracelet/bubbles
//   go get github.com/charmbracelet/lipgloss
//   go mod tidy
//
// See README.md for the planned architecture.
