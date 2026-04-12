# carlos-setup (Go TUI)

A planned terminal UI for installing CARLOS EMR. This directory currently
contains a **skeleton** - the full Bubbletea implementation will land in a
follow-up PR.

## Current behavior

The skeleton binary simply delegates to `../setup.sh`. This lets us
distribute a single binary via GitHub Releases today while the TUI is being
built, without users needing to know the difference.

```sh
./carlos-setup --version       # prints version
./carlos-setup                 # runs setup.sh
./carlos-setup --dry-run       # forwarded to setup.sh
```

## Planned architecture

The TUI will use [Bubbletea](https://github.com/charmbracelet/bubbletea)
with the standard `Model`/`Update`/`View` loop. Screens:

1. **Welcome** - lipgloss banner, project description, quick-start overview.
2. **Prerequisites** - auto-detect podman/docker, disk, RAM, ports. Show a
   checklist of pass/fail items.
3. **Configuration** - form fields (Bubbles `textinput`, `list`) for:
   - Province (on/bc)
   - Database password (with "generate strong" helper)
   - Ports (with conflict warnings)
   - Demo data toggle
   - WAR source (prebuilt / build-from-source)
4. **Review** - display the exact commands that will run. Critical for the
   transparency requirement - users must see what `setup.sh` will do.
5. **Deploy** - spinner per step, live log tail from the container engine.
6. **Complete** - access URL, credentials, next-step commands.

## Design principle: bash script is authoritative

The TUI is a **front-end** to `setup.sh`, not a reimplementation. It will
invoke `setup.sh --non-interactive` with environment variables set from
form input. Reasons:

- Users who can't or won't install Go still get first-class support via the
  shell script.
- One source of truth for install logic.
- The script's commands are auditable by reading one file.
- The TUI can always display the exact shell command it is about to run.

## Build & distribution

```sh
# Single-platform local build
go build -o carlos-setup

# Cross-compile for GitHub Releases
make build   # (Makefile to be added with the TUI implementation)
```

The binary is fully static and has no runtime dependencies - ideal for
distribution to clinics that may not have Go, Python, or Node installed.

## License

GPL-2.0-or-later, consistent with the rest of the CARLOS project.
