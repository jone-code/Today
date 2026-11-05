#!/usr/bin/env node
import { createServer } from "node:http";

const port = Number(process.env.MOCK_QDRANT_PORT || 6339);
let deletes = 0;
let upserts = 0;
const pointIds = new Set();

createServer(async (request, response) => {
  if (request.url === "/__stats") {
    response.writeHead(200, { "content-type": "application/json" });
    response.end(JSON.stringify({ deletes, upserts, pointIds: [...pointIds] }));
    return;
  }
  if (request.method === "GET" && (request.url === "/readyz"
      || request.url === "/collections/today_memories")) {
    response.writeHead(200, { "content-type": "application/json" });
    response.end('{"result":true,"status":"ok"}');
    return;
  }
  if ((request.url?.startsWith("/collections/today_memories/points?")
      || request.url?.startsWith("/collections/today_memories/points/"))
      && (request.method === "POST" || request.method === "PUT")) {
    let body = "";
    for await (const chunk of request) body += chunk;
    try {
      const parsed = JSON.parse(body);
      if (request.url.includes("/delete?")) {
        for (const id of parsed.points || []) pointIds.delete(id);
        deletes++;
      } else if (request.method === "PUT") {
        for (const point of parsed.points || []) pointIds.add(point.id);
        upserts++;
      }
    } catch {
      response.writeHead(400).end();
      return;
    }
    response.writeHead(200, { "content-type": "application/json" });
    response.end('{"result":true,"status":"ok"}');
    return;
  }
  response.writeHead(404).end();
}).listen(port, "127.0.0.1", () => {
  console.log(`mock Qdrant listening on 127.0.0.1:${port}`);
});
