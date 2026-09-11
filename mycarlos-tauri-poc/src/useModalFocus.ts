import { useEffect, useRef, type RefObject } from "react";

const FOCUSABLE_SELECTOR =
  'button:not([disabled]), select:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function useModalFocus(
  active: boolean,
  dialogRef: RefObject<HTMLElement | null>,
  close: () => void,
): void {
  const closeRef = useRef(close);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  closeRef.current = close;

  useEffect(() => {
    if (!active) return;
    returnFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const focusable = () =>
      Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? []);
    focusable()[0]?.focus();

    const containFocus = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeRef.current();
        return;
      }
      if (event.key !== "Tab") return;
      const controls = focusable();
      if (!controls.length) return;
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    window.addEventListener("keydown", containFocus);
    return () => {
      window.removeEventListener("keydown", containFocus);
      returnFocusRef.current?.focus();
    };
  }, [active, dialogRef]);
}
