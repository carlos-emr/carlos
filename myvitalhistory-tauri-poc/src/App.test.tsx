import { render, screen, waitFor, within } from "@testing-library/react";
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
  it("shows the mock-aligned record library and native runtime information", async () => {
    const user = userEvent.setup();
    render(<App bridge={bridge()} />);

    expect(screen.getByRole("heading", { name: "My records" })).toBeVisible();
    expect(screen.getByText("Heart & blood pressure")).toBeVisible();
    expect(screen.getByText("Bloodwork — cholesterol and liver panel")).toBeVisible();
    expect(screen.getByText("Technology evaluation only")).toBeVisible();

    await user.click(screen.getByText("Evaluation details"));
    expect(await screen.findByText("Hello from test Rust")).toBeVisible();
    expect(screen.getByText("test-os")).toBeVisible();
  });

  it("adds selected PDF metadata for this session", async () => {
    const user = userEvent.setup();
    render(
      <App
        bridge={bridge({
          selectPdf: vi.fn().mockResolvedValue({ name: "fictional-record.pdf", sizeBytes: 2048 }),
        })}
      />,
    );

    await user.click(screen.getByRole("button", { name: "New document — choose a sample PDF" }));

    expect(await screen.findByText("fictional-record.pdf")).toBeVisible();
    expect(screen.getByText("fictional-record.pdf was added for this session only.")).toBeVisible();
    expect(screen.getByText("2 KB · PDF · session only")).toBeVisible();
  });

  it("creates and resets session-only library items", async () => {
    const user = userEvent.setup();
    render(<App bridge={bridge()} />);

    await user.click(screen.getByRole("button", { name: "New folder" }));
    expect(screen.getByText("New sample folder 1")).toBeVisible();

    await user.click(screen.getByText("Evaluation details"));
    const reset = screen.getByRole("button", { name: "Reset session" });
    expect(reset).toBeEnabled();
    await user.click(reset);

    expect(screen.queryByText("New sample folder 1")).not.toBeInTheDocument();
    expect(screen.getByText("Evaluation reset. Only the built-in sample records are shown.")).toBeVisible();
    expect(screen.getByText("3 folders · 5 documents · sample data")).toBeVisible();
  });

  it("searches, filters, and switches record views", async () => {
    const user = userEvent.setup();
    render(<App bridge={bridge()} />);

    await user.type(screen.getByRole("searchbox", { name: "Search your records" }), "cardiology");
    expect(screen.getByText("Specialist letter — cardiology")).toBeVisible();
    expect(screen.queryByText("Chest X-ray report")).not.toBeInTheDocument();

    await user.clear(screen.getByRole("searchbox", { name: "Search your records" }));
    await user.click(within(screen.getByLabelText("Filter records")).getByRole("button", { name: "Imaging" }));
    expect(screen.getByText("Chest X-ray report")).toBeVisible();
    expect(screen.queryByText("Specialist letter — cardiology")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Grid view" }));
    expect(screen.getByRole("button", { name: "Grid view" })).toHaveAttribute("aria-pressed", "true");
  });

  it("handles picker cancellation without changing the library", async () => {
    const user = userEvent.setup();
    render(<App bridge={bridge()} />);

    await user.click(screen.getByRole("button", { name: "New document — choose a sample PDF" }));

    expect(await screen.findByText("No file selected. Nothing changed.")).toBeVisible();
    expect(screen.getByText("3 folders · 5 documents · sample data")).toBeVisible();
  });

  it("rejects a non-PDF returned by the platform boundary", async () => {
    const user = userEvent.setup();
    render(
      <App bridge={bridge({ selectPdf: vi.fn().mockResolvedValue({ name: "not-a-record.txt" }) })} />,
    );

    await user.click(screen.getByRole("button", { name: "New document — choose a sample PDF" }));

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

    await user.click(screen.getByText("Evaluation details"));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The runtime information command was unavailable.",
    );
    expect(screen.queryByText(/sensitive internal detail/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "New document — choose a sample PDF" }));
    await waitFor(() =>
      expect(screen.getByText("The file picker could not be opened. No file was accessed.")).toBeVisible(),
    );
    expect(screen.queryByText(/private\/example/)).not.toBeInTheDocument();
  });
});
