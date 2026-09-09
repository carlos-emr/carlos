import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import VaultApp from "./VaultApp";
import type { VaultBridge, VaultSnapshot } from "./vault";

const emptySnapshot: VaultSnapshot = {
  profiles: [{ id: "profile-1", displayName: "Jamie", createdAtMs: 1 }],
  folders: [],
  records: [],
};

function nativeBridge(overrides: Partial<VaultBridge> = {}): VaultBridge {
  return {
    native: true,
    status: vi.fn().mockResolvedValue("locked"),
    create: vi.fn().mockResolvedValue(emptySnapshot),
    unlock: vi.fn().mockResolvedValue(emptySnapshot),
    lock: vi.fn().mockResolvedValue(undefined),
    snapshot: vi.fn().mockResolvedValue(emptySnapshot),
    changePassphrase: vi.fn().mockResolvedValue(undefined),
    createProfile: vi.fn().mockResolvedValue("profile-2"),
    createFolder: vi.fn().mockResolvedValue("folder-1"),
    assignFolders: vi.fn().mockResolvedValue(undefined),
    importFiles: vi.fn().mockResolvedValue({ imported: [], skippedDuplicates: [] }),
    exportFile: vi.fn().mockResolvedValue(false),
    reset: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe("durable vault UI", () => {
  it("creates the vault only when passphrases match", async () => {
    const user = userEvent.setup();
    const bridge = nativeBridge({ status: vi.fn().mockResolvedValue("absent") });
    render(<VaultApp bridge={bridge} />);

    await screen.findByRole("heading", { name: "Create your encrypted vault" });
    await user.type(screen.getByLabelText("First patient profile"), "Jamie");
    const passwords = screen.getAllByLabelText(/passphrase/i);
    await user.type(passwords[0], "Correct#8Horse");
    await user.type(passwords[1], "different");
    expect(screen.getByRole("button", { name: "Create vault" })).toBeDisabled();
    await user.clear(passwords[1]);
    await user.type(passwords[1], "Correct#8Horse");
    await user.click(screen.getByRole("button", { name: "Create vault" }));

    await screen.findByRole("heading", { name: "My records" });
    expect(bridge.create).toHaveBeenCalledWith("Correct#8Horse", "Jamie");
  });

  it("unlocks and imports through the native bridge", async () => {
    const user = userEvent.setup();
    const bridge = nativeBridge({
      importFiles: vi.fn().mockResolvedValue({ imported: ["record-1"], skippedDuplicates: ["copy.pdf"] }),
      snapshot: vi.fn().mockResolvedValue({
        ...emptySnapshot,
        records: [{ id: "record-1", profileId: "profile-1", folderIds: [], displayName: "report.pdf", sourceLabel: "Manual import — unverified", mediaType: "application/octet-stream", plaintextSize: 2048, importedAtMs: 1 }],
      }),
    });
    render(<VaultApp bridge={bridge} />);

    await screen.findByRole("heading", { name: "Unlock your vault" });
    await user.type(screen.getByLabelText("Passphrase"), "Correct#8Horse");
    await user.click(screen.getByRole("button", { name: "Unlock" }));
    await user.click(await screen.findByRole("button", { name: "Choose files to import" }));

    expect(await screen.findByText("report.pdf")).toBeVisible();
    expect(screen.getByText("1 file(s) encrypted and imported. 1 duplicate(s) skipped.")).toBeVisible();
    expect(bridge.importFiles).toHaveBeenCalledWith("profile-1", []);
  });

  it("finishes an active native import before locking a backgrounded app", async () => {
    const user = userEvent.setup();
    let finishImport!: (value: { imported: string[]; skippedDuplicates: string[] }) => void;
    const importFiles = vi.fn().mockReturnValue(new Promise((resolve) => {
      finishImport = resolve;
    }));
    const bridge = nativeBridge({
      status: vi.fn().mockResolvedValue("unlocked"),
      importFiles,
    });
    let visibilityState: DocumentVisibilityState = "visible";
    const visibility = vi.spyOn(document, "visibilityState", "get")
      .mockImplementation(() => visibilityState);
    render(<VaultApp bridge={bridge} />);

    await user.click(await screen.findByRole("button", { name: "Choose files to import" }));
    visibilityState = "hidden";
    document.dispatchEvent(new Event("visibilitychange"));
    expect(bridge.lock).not.toHaveBeenCalled();

    await act(async () => finishImport({ imported: [], skippedDuplicates: [] }));
    await waitFor(() => expect(bridge.lock).toHaveBeenCalledOnce());
    expect(await screen.findByRole("heading", { name: "Unlock your vault" })).toBeVisible();
    visibility.mockRestore();
  });

  it("ignores window blur and locks after 15 minutes of inactivity", async () => {
    vi.useFakeTimers();
    try {
      const bridge = nativeBridge({ status: vi.fn().mockResolvedValue("unlocked") });
      render(<VaultApp bridge={bridge} />);
      await act(async () => undefined);
      expect(screen.getByRole("heading", { name: "My records" })).toBeVisible();

      window.dispatchEvent(new Event("blur"));
      await act(async () => undefined);
      expect(bridge.lock).not.toHaveBeenCalled();

      await act(async () => vi.advanceTimersByTime(15 * 60 * 1000));
      expect(bridge.lock).toHaveBeenCalledOnce();
    } finally {
      vi.useRealTimers();
    }
  });

  it("requires the exact destructive reset phrase", async () => {
    const user = userEvent.setup();
    const bridge = nativeBridge();
    render(<VaultApp bridge={bridge} />);
    await screen.findByRole("heading", { name: "Unlock your vault" });
    await user.click(screen.getByText("Forgot your passphrase?"));
    const erase = screen.getByRole("button", { name: "Erase vault" });
    expect(erase).toBeDisabled();
    await user.type(screen.getByLabelText("Type RESET MYCARLOS VAULT"), "RESET MYCARLOS VAULT");
    expect(erase).toBeEnabled();
    await user.click(erase);
    await waitFor(() => expect(bridge.reset).toHaveBeenCalledWith("RESET MYCARLOS VAULT"));
  });
});
