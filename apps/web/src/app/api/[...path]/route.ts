import { setDefaultResultOrder } from "node:dns";
import http from "node:http";
import https from "node:https";
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

// Docker Compose DNS often returns IPv6 first; Node then fails with "fetch failed".
setDefaultResultOrder("ipv4first");

const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
  "transfer-encoding",
  "upgrade",
  "host",
  "content-length",
]);

function apiTarget(): string {
  const raw = process.env.API_PROXY_TARGET || "http://127.0.0.1:3001";
  return raw.replace(/\/$/, "");
}

function errorDetail(err: unknown): string {
  if (!(err instanceof Error)) return String(err);
  const cause = (err as Error & { cause?: unknown }).cause;
  if (cause instanceof Error) return `${err.message} (${cause.message})`;
  if (cause && typeof cause === "object" && "code" in cause) {
    return `${err.message} (${String((cause as { code: unknown }).code)})`;
  }
  return err.message;
}

/** Build upstream headers; keep Authorization (Next.js patched fetch may drop it). */
function upstreamHeaders(req: NextRequest): http.OutgoingHttpHeaders {
  const out: http.OutgoingHttpHeaders = {};
  req.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (HOP_BY_HOP.has(lower)) return;
    out[key] = value;
  });

  // Explicit auth recovery — some runtimes omit authorization from forEach.
  const auth =
    req.headers.get("authorization") ||
    req.headers.get("Authorization") ||
    req.headers.get("x-today-authorization");
  if (auth) {
    out.authorization = auth;
  }
  // Do not force application/json — media GETs need image/* or */*.
  if (!out.accept) out.accept = "*/*";
  return out;
}

function proxyRequest(
  targetUrl: string,
  method: string,
  headers: http.OutgoingHttpHeaders,
  body?: Buffer,
): Promise<{ status: number; headers: http.IncomingHttpHeaders; body: Buffer }> {
  const url = new URL(targetUrl);
  const lib = url.protocol === "https:" ? https : http;

  return new Promise((resolve, reject) => {
    const req = lib.request(
      {
        protocol: url.protocol,
        hostname: url.hostname,
        port: url.port || (url.protocol === "https:" ? 443 : 80),
        path: `${url.pathname}${url.search}`,
        method,
        headers,
        timeout: 60000,
      },
      (res) => {
        const chunks: Buffer[] = [];
        res.on("data", (c) => chunks.push(Buffer.isBuffer(c) ? c : Buffer.from(c)));
        res.on("end", () => {
          resolve({
            status: res.statusCode ?? 502,
            headers: res.headers,
            body: Buffer.concat(chunks),
          });
        });
      },
    );
    req.on("timeout", () => {
      req.destroy(new Error("upstream timeout"));
    });
    req.on("error", reject);
    if (body && body.length > 0) req.write(body);
    req.end();
  });
}

async function proxy(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> },
): Promise<NextResponse> {
  const { path } = await ctx.params;
  if (!path?.length) {
    return NextResponse.json({ message: "missing api path" }, { status: 404 });
  }

  const target = `${apiTarget()}/${path.join("/")}${req.nextUrl.search}`;
  const headers = upstreamHeaders(req);

  let body: Buffer | undefined;
  if (req.method !== "GET" && req.method !== "HEAD") {
    body = Buffer.from(await req.arrayBuffer());
    headers["content-length"] = body.length;
  }

  try {
    const upstream = await proxyRequest(target, req.method, headers, body);
    const outHeaders = new Headers();
    const contentType = upstream.headers["content-type"];
    if (typeof contentType === "string") {
      outHeaders.set("content-type", contentType);
    }
    return new NextResponse(new Uint8Array(upstream.body), {
      status: upstream.status,
      headers: outHeaders,
    });
  } catch (e) {
    return NextResponse.json(
      {
        statusCode: 502,
        message: `API proxy error (${apiTarget()}): ${errorDetail(e)}. Is today-api up? Try: docker compose ps && docker compose logs api --tail=50`,
      },
      { status: 502 },
    );
  }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
export const OPTIONS = proxy;
