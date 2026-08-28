import { invoke } from "@tauri-apps/api/core";
import { open } from "@tauri-apps/plugin-dialog";

export interface RuntimeInfo {
  platform: string;
  architecture: string;
  appVersion: string;
  message: string;
  native: boolean;
}

export interface SelectedDocument {
  name: string;
  sizeBytes?: number;
}

export interface PlatformBridge {
  getRuntimeInfo(): Promise<RuntimeInfo>;
  selectPdf(): Promise<SelectedDocument | null>;
}

function isTauriRuntime(): boolean {
  return "__TAURI_INTERNALS__" in window;
}

function safeBasename(path: string): string {
  return path.split(/[\\/]/).at(-1) || "Selected document.pdf";
}

function selectPdfInBrowser(): Promise<SelectedDocument | null> {
  return new Promise((resolve) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "application/pdf,.pdf";
    input.addEventListener(
      "change",
      () => {
        const file = input.files?.item(0);
        resolve(file ? { name: file.name, sizeBytes: file.size } : null);
      },
      { once: true },
    );
    input.addEventListener("cancel", () => resolve(null), { once: true });
    input.click();
  });
}

export function createPlatformBridge(): PlatformBridge {
  return {
    async getRuntimeInfo() {
      if (!isTauriRuntime()) {
        return {
          platform: "Browser preview",
          architecture: "web",
          appVersion: "0.1.0",
          message: "Hello from the shared web UI",
          native: false,
        };
      }

      const result = await invoke<Omit<RuntimeInfo, "native">>("runtime_info");
      return { ...result, native: true };
    },

    async selectPdf() {
      if (!isTauriRuntime()) {
        return selectPdfInBrowser();
      }

      const selected = await open({
        multiple: false,
        directory: false,
        filters: [{ name: "PDF documents", extensions: ["pdf"] }],
      });
      return selected ? { name: safeBasename(selected) } : null;
    },
  };
}
