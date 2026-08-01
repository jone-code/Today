"use client";

import { useEffect, useState } from "react";
import { apiFetchMediaObjectUrl } from "@/shared/lib/api-client";

type Props = {
  src: string;
  alt: string;
  className?: string;
};

type LoadState =
  | { status: "loading" }
  | { status: "ready"; objectUrl: string }
  | { status: "failed" };

/** Loads media that requires Bearer auth (img src cannot send Authorization). */
export function AuthedPhoto({ src, alt, className }: Props) {
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    apiFetchMediaObjectUrl(src)
      .then((url) => {
        if (cancelled) {
          URL.revokeObjectURL(url);
          return;
        }
        objectUrl = url;
        setState({ status: "ready", objectUrl: url });
      })
      .catch(() => {
        if (!cancelled) setState({ status: "failed" });
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [src]);

  if (state.status === "failed") {
    return <span className="muted">照片加载失败</span>;
  }
  if (state.status !== "ready") {
    return <span className="muted">加载照片…</span>;
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element -- blob URL from authenticated fetch
    <img src={state.objectUrl} alt={alt} className={className} />
  );
}
