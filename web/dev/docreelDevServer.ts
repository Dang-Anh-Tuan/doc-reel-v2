/**
 * Plugin Vite chỉ chạy ở `npm run dev`.
 *
 * - Phục vụ template / static / samples của repo theo đúng đường dẫn Spring Boot sẽ phục vụ,
 *   nên template Thymeleaf dùng chung `@{/css/base.css}`:
 *     /css, /js, /vendor, /static → src/main/resources/static/...
 *     /templates               → src/main/resources/templates/...
 *     /samples                 → samples/...
 * - GET /__dev/samples         → danh sách file mẫu (chỉ dev; không đụng /api của Spring).
 * - Theo dõi templates/static/samples: đổi file → gửi event `docreel:changed` qua HMR
 *   để trang preview tự render lại.
 */
import { readFile, readdir, stat } from "node:fs/promises";
import type { ServerResponse } from "node:http";
import { extname, resolve, sep } from "node:path";
import type { Plugin } from "vite";

export const DEV_EVENT = "docreel:changed";

const CONTENT_TYPES: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".ttf": "font/ttf",
  ".woff2": "font/woff2",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".txt": "text/plain; charset=utf-8",
};

interface Options {
  /** Thư mục gốc repo (chứa src/main/resources và samples). */
  repoRoot: string;
}

export function docreelDevServer({ repoRoot }: Options): Plugin {
  const dirs = {
    static: resolve(repoRoot, "src/main/resources/static"),
    templates: resolve(repoRoot, "src/main/resources/templates"),
    samples: resolve(repoRoot, "samples"),
  };
  const mounts: Array<[prefix: string, dir: string]> = [
    ["/css/", resolve(dirs.static, "css")],
    ["/js/", resolve(dirs.static, "js")],
    ["/vendor/", resolve(dirs.static, "vendor")],
    ["/static/", dirs.static],
    ["/templates/", dirs.templates],
    ["/samples/", dirs.samples],
  ];

  const send = (res: ServerResponse, status: number, body: string | Buffer, type = "text/plain; charset=utf-8") => {
    res.writeHead(status, { "content-type": type, "cache-control": "no-store" });
    res.end(body);
  };

  /** Trả về đường dẫn tuyệt đối nếu nằm trong `base`, ngược lại null (chặn ../). */
  const inside = (base: string, rest: string): string | null => {
    const target = resolve(base, decodeURIComponent(rest));
    return target === base || target.startsWith(base + sep) ? target : null;
  };

  const listJson = async (dir: string) =>
    (await readdir(dir).catch(() => [] as string[])).filter((f) => f.endsWith(".json")).sort();

  return {
    name: "docreel-dev-server",
    apply: "serve",
    configureServer(server) {
      server.watcher.add([dirs.templates, dirs.static, dirs.samples]);
      server.watcher.on("change", (file) => {
        if ([dirs.templates, dirs.static, dirs.samples].some((d) => file.startsWith(d))) {
          server.ws.send({ type: "custom", event: DEV_EVENT, data: { file } });
        }
      });

      server.middlewares.use(async (req, res, next) => {
        const path = new URL(req.url ?? "/", "http://localhost").pathname;
        try {
          if (path === "/__dev/samples") {
            const body = {
              scenes: await listJson(resolve(dirs.samples, "scenes")),
              full: await listJson(resolve(dirs.samples, "full")),
            };
            return send(res, 200, JSON.stringify(body), CONTENT_TYPES[".json"]);
          }
          const mount = mounts.find(([prefix]) => path.startsWith(prefix));
          if (!mount) return next();
          const file = inside(mount[1], path.slice(mount[0].length));
          const info = file ? await stat(file).catch(() => null) : null;
          if (!file || !info?.isFile()) return send(res, 404, "not found");
          const type = CONTENT_TYPES[extname(file).toLowerCase()] ?? "application/octet-stream";
          return send(res, 200, await readFile(file), type);
        } catch (err) {
          return send(res, 500, String(err));
        }
      });
    },
  };
}
