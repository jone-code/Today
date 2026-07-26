import { setDefaultResultOrder } from "node:dns";
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

// Docker Compose DNS often returns IPv6 first; Node fetch then fails with "fetch failed".
setDefaultResultOrder("ipv4first");

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

async function proxy(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> },
): Promise<NextResponse> {
  const { path } = await ctx.params;
  if (!path?.length) {
    return NextResponse.json({ message: "missing api path" }, { status: 404 });
  }

  const target = `${apiTarget()}/${path.join("/")}${req.nextUrl.search}`;
  const headers = new Headers();
  const contentType = req.headers.get("content-type");
  const authorization = req.headers.get("authorization");
  if (contentType) headers.set("content-type", contentType);
  if (authorization) headers.set("authorization", authorization);
  headers.set("accept", req.headers.get("accept") || "application/json");

  const init: RequestInit = {
    method: req.method,
    headers,
    redirect: "manual",
    cache: "no-store",
  };

  if (req.method !== "GET" && req.method !== "HEAD") {
    init.body = await req.arrayBuffer();
  }

  try {
    const upstream = await fetch(target, init);
    const body = await upstream.arrayBuffer();
    const out = new NextResponse(body, { status: upstream.status });
    const upstreamType = upstream.headers.get("content-type");
    if (upstreamType) out.headers.set("content-type", upstreamType);
    return out;
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
