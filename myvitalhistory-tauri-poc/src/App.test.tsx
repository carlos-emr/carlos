import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import App from "./App";
import type { PlatformBridge } from "./platform";

function bridge(overrides: Partial<PlatformBridge> = {}): PlatformBridge {
  return {
    getRuntimeInfo: vi.fn().mockResolvedValue({
      platform: "test-os",
      architecture: "test-arch",
      appVersion: "0.1.0",
      message: "Hello from test Rust",
      native: true,
    }),
    selectPdf: vi.fn().mockResolvedValue(null),
    ...overrides,
  };
}

describe("MyVitalHistory Tauri evaluation", () => {
  it("shows synthetic records and native runtime information", async () => {
    render(<App bridge={bridge()} />);

    expect(screen.getByRole("heading", { name: "Your health records, kept by you" })).toBeVisible();
    expect(screen.getByText("Sample referral letter")).toBeVisible();
    expect(await screen.findByText("Hello from test Rust")).toBeVisible();
    expect(screen.getByText("test-os")).toBeVisible();
  });

  it("adds a selected PDF as session-only metadata", async () => {
    const user = userEvent.setup();
    render(
      <App
        bridge={bridge({
          selectPdf: vi.fn().mockResolvedValue({ name: "fictional-record.pdf", sizeBytes: 2048 }),
        })}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Choose a sample PDF" }));

    expect(await screen.findByText("fictional-record.pdf")).toBeVisible();
    expect(screen.getByText("fictional-record.pdf was added for this session only.")).toBeVisible();
    expect(screen.getByText("Session only", { exact: true })).toBeVisible();
  });

  it("handles picker cancellation without changing the library", async () => {
    const user = userEvent.setup();
    render(<App bridge={bridge()} />);

    await user.click(screen.getByRole("button", { name: "Choose a sample PDF" }));

    expect(await screen.findByText("No file selected. Nothing changed.")).toBeVisible();
    expect(screen.getByText("3 items")).toBeVisible();
  });

  it("rejects a non-PDF returned by the platform boundary", async () => {
    const user = userEvent.setup();
    render(
      <App bridge={bridge({ selectPdf: vi.fn().mockResolvedValue({ name: "not-a-record.txt" }) })} />,
    );

    await user.click(screen.getByRole("button", { name: "Choose a sample PDF" }));

    expect(await screen.findByText("This evaluation accepts PDF files only.")).toBeVisible();
    expect(screen.queryByText("not-a-record.txt")).not.toBeInTheDocument();
  });

  it("reports platform failures without exposing details", async () => {
    const user = userEvent.setup();
    render(
      <App
        bridge={bridge({
          getRuntimeInfo: vi.fn().mockRejectedValue(new Error("sensitive internal detail")),
          selectPdf: vi.fn().mockRejectedValue(new Error("/private/example.pdf")),
        })}
      />,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The runtime information command was unavailable.",
    );
    expect(screen.queryByText(/sensitive internal detail/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Choose a sample PDF" }));
    await waitFor(() =>
      expect(screen.getByText("The file picker could not be opened. No file was accessed.")).toBeVisible(),
    );
    expect(screen.queryByText(/private\/example/)).not.toBeInTheDocument();
  });
});
