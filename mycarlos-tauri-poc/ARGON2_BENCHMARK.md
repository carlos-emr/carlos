# Argon2id device benchmark record

- **Purpose:** tune copied-vault guessing cost without making patient unlock unusable
- **Current parameters:** Argon2id v1.3, 64 MiB, 3 iterations, 4 lanes, 32-byte output
- **Status:** repeatable harness implemented; representative physical-device results open

Run the benchmark in release mode on an otherwise idle target:

```sh
cargo test --release --manifest-path src-tauri/Cargo.toml \
  benchmark_argon2id_unlock_work_factor -- --ignored --nocapture --test-threads=1
```

The harness performs five derivations and reports every sample, the median, and the maximum. Do not
run it in debug mode, compare virtualized CI timings with physical devices, or record the test
passphrase as though it were a patient credential. Mobile results need a thin on-device test wrapper
around the same Rust function; simulator/emulator numbers are supplementary only.

## Required measurements

| Platform | Oldest supported physical device/OS | Build and toolchain | Samples (ms) | Median/max | Peak memory | Thermal/battery notes | Reviewer |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Windows | Pending | Pending | Pending | Pending | Pending | Pending | Pending |
| macOS | Pending | Pending | Pending | Pending | Pending | Pending | Pending |
| Android | Pending | Pending | Pending | Pending | Pending | Pending | Pending |
| iOS | Pending | Pending | Pending | Pending | Pending | Pending | Pending |

For every target, also measure cold launch plus unlock, five consecutive unlocks, background/resume,
and the slowest supported device while thermally constrained. Record whether the UI remains
responsive and whether the OS terminates the process under memory pressure.

## Decision gate

The application owner and independent security reviewer must approve a versioned parameter set only
after the physical-device table is complete. If parameters change, increment the stored KDF policy
version or add an explicitly supported parameter profile; never reinterpret an existing header's
parameters. Rewrap the random master key after a successful authenticated unlock rather than
rewriting document objects.
