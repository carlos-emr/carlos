import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
    updateFolder: vi.fn().mockResolvedValue(undefined),
    assignFolders: vi.fn().mockResolvedValue(undefined),
    importFiles: vi.fn().mockResolvedValue({ imported: [], skippedDuplicates: [] }),
    exportFile: vi.fn().mockResolvedValue(false),
    deleteRecord: vi.fn().mockResolvedValue(undefined),
    reset: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

function dragTransfer() {
  let payload = "";
  return {
    effectAllowed: "none",
    dropEffect: "none",
    setData: vi.fn((_type: string, value: string) => { payload = value; }),
    getData: vi.fn(() => payload),
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

  it("presents durable records as a navigable filing cabinet", async () => {
    const user = userEvent.setup();
    const filingSnapshot: VaultSnapshot = {
      profiles: [{ id: "profile-1", displayName: "FAKE Avery Patient", createdAtMs: 1 }],
      folders: [
        { id: "folder-1", profileId: "profile-1", parentId: null, name: "FAKE Test Results", createdAtMs: 2 },
        { id: "folder-2", profileId: "profile-1", parentId: null, name: "FAKE Letters", createdAtMs: 2 },
        { id: "folder-3", profileId: "profile-1", parentId: "folder-2", name: "FAKE 2025 Letters", createdAtMs: 2 },
      ],
      records: [
        { id: "record-root", profileId: "profile-1", folderIds: [], displayName: "FAKE_Root_Letter.pdf", sourceLabel: "Manual import — unverified", mediaType: "application/octet-stream", plaintextSize: 1024, importedAtMs: 3 },
        { id: "record-folder", profileId: "profile-1", folderIds: ["folder-1"], displayName: "FAKE_Bloodwork.pdf", sourceLabel: "Manual import — unverified", mediaType: "application/octet-stream", plaintextSize: 2048, importedAtMs: 4 },
      ],
    };
    const bridge = nativeBridge({
      status: vi.fn().mockResolvedValue("unlocked"),
      snapshot: vi.fn().mockResolvedValue(filingSnapshot),
    });
    render(<VaultApp bridge={bridge} />);

    expect(await screen.findByRole("heading", { name: "My records" })).toBeVisible();
    expect(screen.getByText("FAKE_Root_Letter.pdf")).toBeVisible();
    expect(screen.queryByText("FAKE_Bloodwork.pdf")).not.toBeInTheDocument();

    const recordTransfer = dragTransfer();
    fireEvent.dragStart(screen.getByRole("article", { name: "FAKE_Root_Letter.pdf document" }), { dataTransfer: recordTransfer });
    const visibleFolder = screen.getByRole("article", { name: "FAKE Test Results folder" });
    const folderNavigation = screen.getByRole("navigation", { name: "Record library" });
    const sidebarFolder = within(folderNavigation).getByRole("button", { name: /FAKE Test Results/ });
    fireEvent.dragOver(visibleFolder, { dataTransfer: recordTransfer });
    expect(visibleFolder).toHaveClass("native-drop-target");
    expect(sidebarFolder).not.toHaveClass("native-drop-target");
    fireEvent.drop(visibleFolder, { dataTransfer: recordTransfer });
    await waitFor(() => expect(bridge.assignFolders).toHaveBeenCalledWith("record-root", ["folder-1"]));

    const nestedTransfer = dragTransfer();
    fireEvent.dragStart(screen.getByRole("article", { name: "FAKE_Root_Letter.pdf document" }), { dataTransfer: nestedTransfer });
    const nestedSidebarFolder = within(folderNavigation).getByRole("button", { name: /FAKE 2025 Letters/ });
    fireEvent.dragOver(nestedSidebarFolder, { dataTransfer: nestedTransfer });
    fireEvent.drop(nestedSidebarFolder, { dataTransfer: nestedTransfer });
    await waitFor(() => expect(bridge.assignFolders).toHaveBeenCalledWith("record-root", ["folder-3"]));

    const folderTransfer = dragTransfer();
    fireEvent.dragStart(screen.getByRole("article", { name: "FAKE Test Results folder" }), { dataTransfer: folderTransfer });
    fireEvent.dragOver(screen.getByRole("article", { name: "FAKE Letters folder" }), { dataTransfer: folderTransfer });
    fireEvent.drop(screen.getByRole("article", { name: "FAKE Letters folder" }), { dataTransfer: folderTransfer });
    await waitFor(() => expect(bridge.updateFolder).toHaveBeenCalledWith("folder-1", "folder-2", "FAKE Test Results"));

    await user.click(screen.getByRole("button", { name: "Open FAKE Test Results" }));
    expect(screen.getByRole("heading", { name: "FAKE Test Results" })).toBeVisible();
    expect(screen.getByText("FAKE_Bloodwork.pdf")).toBeVisible();

    const rootTransfer = dragTransfer();
    fireEvent.dragStart(screen.getByRole("article", { name: "FAKE_Bloodwork.pdf document" }), { dataTransfer: rootTransfer });
    const rootDropTarget = within(folderNavigation).getByRole("button", { name: /My records/ });
    fireEvent.dragOver(rootDropTarget, { dataTransfer: rootTransfer });
    fireEvent.drop(rootDropTarget, { dataTransfer: rootTransfer });
    await waitFor(() => expect(bridge.assignFolders).toHaveBeenCalledWith("record-folder", []));

    await user.click(screen.getByRole("button", { name: "Select FAKE_Bloodwork.pdf" }));
    await user.selectOptions(screen.getByLabelText("Move selected to"), "");
    await user.click(screen.getByRole("button", { name: "Move" }));
    await waitFor(() => expect(bridge.assignFolders).toHaveBeenCalledWith("record-folder", []));

    await user.click(screen.getByText("FAKE_Bloodwork.pdf"));
    expect(screen.getByRole("button", { name: "Save a copy to this computer" })).toBeVisible();
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

  it("requires confirmation before permanently deleting a durable record", async () => {
    const user = userEvent.setup();
    const record = { id: "record-1", profileId: "profile-1", folderIds: [], displayName: "FAKE_Report.pdf", sourceLabel: "Manual import — unverified", mediaType: "application/octet-stream", plaintextSize: 2048, importedAtMs: 1 };
    const deleteRecord = vi.fn().mockResolvedValue(undefined);
    const snapshot = vi.fn()
      .mockResolvedValueOnce({ ...emptySnapshot, records: [record] })
      .mockResolvedValueOnce(emptySnapshot);
    const bridge = nativeBridge({
      status: vi.fn().mockResolvedValue("unlocked"),
      snapshot,
      deleteRecord,
    });
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<VaultApp bridge={bridge} />);

    await user.click(await screen.findByText("FAKE_Report.pdf"));
    await user.click(screen.getByRole("button", { name: "Permanently delete" }));
    expect(deleteRecord).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Permanently delete" }));
    await waitFor(() => expect(deleteRecord).toHaveBeenCalledWith("record-1"));
    expect(await screen.findByText("FAKE_Report.pdf was permanently deleted.")).toBeVisible();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    confirm.mockRestore();
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
