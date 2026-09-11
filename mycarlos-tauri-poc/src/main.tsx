import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import VaultApp from "./VaultApp";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <VaultApp />
  </StrictMode>,
);
