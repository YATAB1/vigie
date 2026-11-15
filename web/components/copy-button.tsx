"use client";

import { useState } from "react";

export function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be refused: the value stays selectable on screen.
    }
  }

  return (
    <button type="button" className="button button-small" onClick={copy} aria-label={label}>
      {copied ? "Copié" : "Copier"}
    </button>
  );
}
